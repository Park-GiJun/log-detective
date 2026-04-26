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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class GeneratorCommandHandler(
    private val scenarioPersistencePort: ScenarioPersistencePort,
    private val ingestSendClientPort: IngestSendClientPort,
    private val ingestSendMessagePort: IngestSendMessagePort,
    private val ingestSendFilePort: IngestSendFilePort,
    private val generatorStateCachePort: GeneratorStateCachePort,
) : StartGeneratorUseCase, StopGeneratorUseCase, BurstGeneratorUseCase, DisposableBean {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // 시나리오별 실행 Job — 동시에 여러 시나리오 가동 지원
    private val jobs: ConcurrentHashMap<Long, Job> = ConcurrentHashMap()

    // burst 는 동일 시나리오에 대해 중복 호출이 가능 → (scenarioId, sequence) 키로 모든 진행 중 burst Job 추적
    // stop / stopAll / shutdown 시 누락 없이 cancel 하기 위해 jobs 와 별도 맵으로 분리한다
    private val burstJobs: ConcurrentHashMap<BurstKey, Job> = ConcurrentHashMap()
    private val burstSequence: AtomicLong = AtomicLong(0L)

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
        val burstCancelled = cancelBurstsFor(scenarioId)

        if (job == null && burstCancelled == 0) {
            logger.warn("Generator가 실행 중이 아닙니다 — scenarioId={}", scenarioId)
            return
        }
        job?.cancel()
        generatorStateCachePort.markStopped(scenarioId)

        val status = generatorStateCachePort.getStatus(scenarioId)
        logger.info(
            "Generator 중지 — scenarioId={}, burst 취소: {}, 총 sent: {}, failed: {}",
            scenarioId, burstCancelled, status.totalSent, status.totalFailed,
        )
    }

    override fun stopAll() {
        // 시나리오 Job 과 burst Job 양쪽에서 시나리오 ID 수집 (burst 만 진행 중인 경우도 stop 대상)
        val ids = (jobs.keys + burstJobs.keys.map { it.scenarioId }).toSet()
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

        // 같은 시나리오에 대해 burst 가 동시에 여러 번 호출될 수 있으므로 sequence 로 키를 유일화한다
        val key = BurstKey(scenarioId, burstSequence.incrementAndGet())
        val job = scope.launch {
            try {
                sendBatch(scenario, count, fraudRatio, Semaphore(MAX_CONCURRENT_BURST))
                logger.info("Burst 전송 완료 — scenarioId={}, count: {}", scenarioId, count)
            } finally {
                // 정상/취소/예외 모두에서 추적 맵을 정리한다
                burstJobs.remove(key)
            }
        }
        burstJobs[key] = job
    }

    override fun destroy() {
        // Spring 컨텍스트 종료 시 호출 → 진행 중인 모든 시나리오/burst Job 을 취소하고 scope 자체를 닫는다
        val runningScenarios = jobs.size
        val runningBursts = burstJobs.size
        jobs.clear()
        burstJobs.clear()
        scope.cancel("GeneratorCommandHandler shutdown")
        if (runningScenarios > 0 || runningBursts > 0) {
            logger.info(
                "GeneratorCommandHandler shutdown — 중단된 시나리오: {}, 중단된 burst: {}",
                runningScenarios, runningBursts,
            )
        }
    }

    /** 테스트/관리용 — 특정 시나리오가 현재 실행 중인지 조회. */
    fun isRunning(scenarioId: Long): Boolean = jobs.containsKey(scenarioId)

    /** 테스트/관리용 — 특정 시나리오의 진행 중 burst 개수. */
    fun activeBurstCount(scenarioId: Long): Int =
        burstJobs.keys.count { it.scenarioId == scenarioId }

    private fun cancelBurstsFor(scenarioId: Long): Int {
        val keys = burstJobs.keys.filter { it.scenarioId == scenarioId }
        keys.forEach { key ->
            burstJobs.remove(key)?.cancel()
        }
        return keys.size
    }

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

    private data class BurstKey(val scenarioId: Long, val sequence: Long)

    companion object {
        private const val MAX_CONCURRENT_SEND = 200
        private const val MAX_CONCURRENT_BURST = 200
        private const val ONE_SECOND_MS = 1_000L
        private const val STATS_LOG_INTERVAL_MULTIPLIER = 10L

        // Scenario.fraudRatio 는 0~100 퍼센트 (Long) — Double 변환용 분모
        private const val FRAUD_RATIO_SCALE = 100.0
    }
}
