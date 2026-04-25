package com.gijun.logdetect.generator.application.handler.command

import com.gijun.logdetect.generator.application.dto.command.GeneratorStartCommand
import com.gijun.logdetect.generator.application.port.`in`.command.BurstGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.StartGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.StopGeneratorUseCase
import com.gijun.logdetect.generator.application.port.out.GeneratorStateCachePort
import com.gijun.logdetect.generator.application.port.out.IngestSendClientPort
import com.gijun.logdetect.generator.application.port.out.IngestSendFilePort
import com.gijun.logdetect.generator.application.port.out.IngestSendMessagePort
import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort
import com.gijun.logdetect.generator.domain.enums.RequestType
import com.gijun.logdetect.generator.domain.model.LogEvent
import com.gijun.logdetect.generator.domain.model.Scenario
import com.gijun.logdetect.generator.domain.objects.LogEventFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class GeneratorCommandHandler(
    private val scenarioPersistencePort: ScenarioPersistencePort,
    private val ingestSendClientPort: IngestSendClientPort,
    private val ingestSendMessagePort: IngestSendMessagePort,
    private val ingestSendFilePort: IngestSendFilePort,
    private val generatorStateCachePort: GeneratorStateCachePort,
) : StartGeneratorUseCase, StopGeneratorUseCase, BurstGeneratorUseCase {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 시나리오별 실행 Job — 동시에 여러 시나리오 가동 지원
    private val jobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    override fun startGenerator(command: GeneratorStartCommand) {
        val scenario = loadScenario(command.scenarioId)
        val rate = scenario.rate.toInt()
        val fraudRatio = scenario.fraudRatio / FRAUD_RATIO_SCALE
        val scenarioId = requireNotNull(scenario.id) { "저장된 시나리오만 실행 가능합니다" }

        // 이미 실행 중이면 무시 (이중 실행 방지)
        if (jobs.containsKey(scenarioId)) {
            logger.warn("Generator가 이미 실행 중입니다 — scenarioId={}", scenarioId)
            return
        }

        logger.info(
            "Generator 시작 — scenario: #{} {}, type: {}, attack: {}, rate: {}, fraudRatio: {}, successful: {}",
            scenarioId, scenario.name, scenario.type, scenario.attackType, rate, fraudRatio, scenario.successful,
        )
        generatorStateCachePort.resetCounters(scenarioId)
        generatorStateCachePort.markRunning(scenarioId, rate)

        val job = scope.launch {
            var lastLogCount = 0L
            val logInterval = rate * STATS_LOG_INTERVAL_MULTIPLIER
            val semaphore = Semaphore(MAX_CONCURRENT_SEND)

            while (isActive) {
                val batchStart = System.currentTimeMillis()

                sendBatch(scenario, rate, fraudRatio, semaphore)

                val sent = generatorStateCachePort.getStatus(scenarioId).totalSent
                if (logInterval > 0 && sent / logInterval > lastLogCount / logInterval) {
                    lastLogCount = sent
                    val status = generatorStateCachePort.getStatus(scenarioId)
                    logger.info(
                        "Generator 통계 — scenarioId={}, sent: {}, failed: {}",
                        scenarioId, status.totalSent, status.totalFailed,
                    )
                }

                val elapsed = System.currentTimeMillis() - batchStart
                val sleepTime = ONE_SECOND_MS - elapsed
                if (sleepTime > 0) delay(sleepTime.milliseconds)
            }
        }
        jobs[scenarioId] = job
    }

    override fun stopGenerator(scenarioId: Long) {
        val job = jobs.remove(scenarioId)
        if (job == null) {
            logger.warn("Generator가 실행 중이 아닙니다 — scenarioId={}", scenarioId)
            return
        }
        job.cancel()
        generatorStateCachePort.markStopped(scenarioId)

        val status = generatorStateCachePort.getStatus(scenarioId)
        logger.info(
            "Generator 중지 — scenarioId={}, 총 sent: {}, failed: {}",
            scenarioId, status.totalSent, status.totalFailed,
        )
    }

    override fun stopAll() {
        val ids = jobs.keys.toList()
        if (ids.isEmpty()) {
            logger.warn("실행 중인 Generator 가 없습니다")
            return
        }
        ids.forEach { stopGenerator(it) }
        logger.info("전체 Generator 중지 — 시나리오 {}개", ids.size)
    }

    override fun burstGenerator(command: GeneratorStartCommand) {
        val scenario = loadScenario(command.scenarioId)
        val count = scenario.rate.toInt()
        val fraudRatio = scenario.fraudRatio / FRAUD_RATIO_SCALE
        val scenarioId = requireNotNull(scenario.id) { "저장된 시나리오만 실행 가능합니다" }

        logger.info(
            "Burst 전송 시작 — scenario: #{} {}, count: {}, fraudRatio: {}",
            scenarioId, scenario.name, count, fraudRatio,
        )

        scope.launch {
            sendBatch(scenario, count, fraudRatio, Semaphore(MAX_CONCURRENT_BURST))
            logger.info("Burst 전송 완료 — scenarioId={}, count: {}", scenarioId, count)
        }
    }

    /** 테스트/관리용 — 특정 시나리오가 현재 실행 중인지 조회. */
    fun isRunning(scenarioId: Long): Boolean = jobs.containsKey(scenarioId)

    private fun loadScenario(scenarioId: Long): Scenario =
        requireNotNull(scenarioPersistencePort.findById(scenarioId)) {
            "시나리오를 찾을 수 없습니다: id=$scenarioId"
        }

    private suspend fun sendBatch(scenario: Scenario, count: Int, fraudRatio: Double, semaphore: Semaphore) {
        val scenarioId = scenario.id ?: return
        coroutineScope {
            repeat(count) {
                launch {
                    semaphore.withPermit {
                        val event = generateLogEvent(scenario, fraudRatio)
                        if (sendByRequestType(scenario.type, event)) {
                            generatorStateCachePort.incrementSent(scenarioId)
                        } else {
                            generatorStateCachePort.incrementFailed(scenarioId)
                        }
                    }
                }
            }
        }
    }

    private fun generateLogEvent(scenario: Scenario, fraudRatio: Double) =
        if (Random.nextDouble() < fraudRatio) {
            LogEventFactory.createSuspicious(scenario.attackType, scenario.successful)
        } else {
            LogEventFactory.createNormal()
        }

    private suspend fun sendByRequestType(type: RequestType, event: LogEvent): Boolean = when (type) {
        RequestType.REST -> ingestSendClientPort.send(event)
        RequestType.KAFKA -> ingestSendMessagePort.send(event)
        RequestType.FILE -> ingestSendFilePort.send(event)
    }

    companion object {
        private const val MAX_CONCURRENT_SEND = 200
        private const val MAX_CONCURRENT_BURST = 200
        private const val ONE_SECOND_MS = 1_000L
        private const val STATS_LOG_INTERVAL_MULTIPLIER = 10L

        // Scenario.fraudRatio 는 0~100 퍼센트 (Long) — Double 변환용 분모
        private const val FRAUD_RATIO_SCALE = 100.0
    }
}
