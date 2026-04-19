package com.gijun.logdetect.generator.application.handler.command

import com.gijun.logdetect.generator.application.dto.command.GeneratorStartCommand
import com.gijun.logdetect.generator.application.port.`in`.command.StartGeneratorUseCase
import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.objects.LogEventFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class GeneratorCommandHandler(
) : StartGeneratorUseCase {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val running = AtomicBoolean(false)

    private val totalSent = AtomicLong(0)

    private val totalFailed = AtomicLong(0)

    private val currentRate = AtomicInteger(0)

    private var job: Job? = null

    override fun startGenerator(command: GeneratorStartCommand) {
        require(command.rate > 0) { "Rate는 항상 양수여야합니다. ${command.rate}" }
        require(command.fraudRatio in 0.0..1.0) { "Fraud Ratio는 누구 보다 열심히" }

        if (running.getAndSet(true)) {
            logger.error("Generator is already running")
            return
        }

        logger.info("Generator is starting  rate : '${command.rate}', fraudRatio : '${command.fraudRatio}'")
        totalSent.set(0)
        totalFailed.set(0)
        currentRate.set(command.rate)

        job = scope.launch {
            var lastLogCount = 0L
            val logInternal = command.rate * 10L
            val semaphore = Semaphore(MAX_CONCURRENT_SEND)

            while (isActive && running.get()) {
                val batchStart = System.currentTimeMillis()

                coroutineScope {
                    repeat(command.rate) {
                        launch { semaphore.acquire() }
                        try {
                            val event = generateLogEvent(command.fraudRatio)
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
