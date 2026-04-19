package com.gijun.logdetect.generator.application.handler.command

import com.gijun.logdetect.generator.application.dto.command.GeneratorStartCommand
import com.gijun.logdetect.generator.application.port.`in`.command.BurstGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.StartGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.StopGeneratorUseCase
import com.gijun.logdetect.generator.application.port.out.IngestSendClientPort
import com.gijun.logdetect.generator.domain.enums.AttackType
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class GeneratorCommandHandler(
    private val ingestSendClientPort: IngestSendClientPort,
) : StartGeneratorUseCase, StopGeneratorUseCase, BurstGeneratorUseCase {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val running = AtomicBoolean(false)

    val totalSent = AtomicLong(0)

    val totalFailed = AtomicLong(0)

    val currentRate = AtomicInteger(0)

    private var job: Job? = null

    override fun startGenerator(command: GeneratorStartCommand) {
        validateCommand(command)

        if (running.getAndSet(true)) {
            logger.warn("Generator가 이미 실행 중입니다")
            return
        }

        logger.info("Generator 시작 — rate: {}, fraudRatio: {}", command.rate, command.fraudRatio)
        totalSent.set(0)
        totalFailed.set(0)
        currentRate.set(command.rate)

        job = scope.launch {
            var lastLogCount = 0L
            val logInterval = command.rate * 10L
            val semaphore = Semaphore(MAX_CONCURRENT_SEND)

            while (isActive && running.get()) {
                val batchStart = System.currentTimeMillis()

                sendBatch(command.rate, command.fraudRatio, semaphore)

                val sent = totalSent.get()
                if (logInterval > 0 && sent / logInterval > lastLogCount / logInterval) {
                    lastLogCount = sent
                    logger.info("Generator 통계 — sent: {}, failed: {}", sent, totalFailed.get())
                }

                val elapsed = System.currentTimeMillis() - batchStart
                val sleepTime = 1000L - elapsed
                if (sleepTime > 0) delay(sleepTime.milliseconds)
            }
        }
    }

    override fun stopGenerator() {
        if (!running.getAndSet(false)) {
            logger.warn("Generator가 실행 중이 아닙니다")
            return
        }

        job?.cancel()
        job = null
        currentRate.set(0)
        logger.info("Generator 중지 — 총 sent: {}, failed: {}", totalSent.get(), totalFailed.get())
    }

    override fun burstGenerator(command: GeneratorStartCommand) {
        validateCommand(command)

        logger.info("Burst 전송 시작 — count: {}, fraudRatio: {}", command.rate, command.fraudRatio)

        scope.launch {
            sendBatch(command.rate, command.fraudRatio, Semaphore(MAX_CONCURRENT_BURST))
            logger.info("Burst 전송 완료 — count: {}", command.rate)
        }
    }

    fun isRunning(): Boolean = running.get()

    private fun validateCommand(command: GeneratorStartCommand) {
        require(command.rate > 0) { "rate는 양수여야 합니다: ${command.rate}" }
        require(command.fraudRatio in 0.0..1.0) { "fraudRatio는 0.0~1.0 범위여야 합니다: ${command.fraudRatio}" }
    }

    private suspend fun sendBatch(count: Int, fraudRatio: Double, semaphore: Semaphore) {
        coroutineScope {
            repeat(count) {
                launch {
                    semaphore.withPermit {
                        val event = generateLogEvent(fraudRatio)
                        if (ingestSendClientPort.send(event)) {
                            totalSent.incrementAndGet()
                        } else {
                            totalFailed.incrementAndGet()
                        }
                    }
                }
            }
        }
    }

    private fun generateLogEvent(fraudRatio: Double) =
        if (Random.nextDouble() < fraudRatio) {
            LogEventFactory.createSuspicious(AttackType.entries.random())
        } else {
            LogEventFactory.createNormal()
        }

    companion object {
        private const val MAX_CONCURRENT_SEND = 200
        private const val MAX_CONCURRENT_BURST = 200
    }
}
