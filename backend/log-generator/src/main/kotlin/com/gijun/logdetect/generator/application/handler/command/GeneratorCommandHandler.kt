package com.gijun.logdetect.generator.application.handler.command

import com.gijun.logdetect.generator.application.dto.command.GeneratorStartCommand
import com.gijun.logdetect.generator.application.port.`in`.command.StartGeneratorUseCase
import com.gijun.logdetect.generator.application.port.out.IngestSendClientPort
import io.ktor.client.request.request
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class GeneratorCommandHandler(
    private val ingestSendClientPort: IngestSendClientPort
) : StartGeneratorUseCase {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val scope : CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val running = AtomicBoolean(false)

    private val totalSent = AtomicLong(0)

    private val totalFailed = AtomicLong(0)

    private val currentRate = AtomicInteger(0)

    private var job: Job? = null

    override fun startGenerator(command : GeneratorStartCommand) {
        require(command.rate > 0 ) { "Rate는 항상 양수여야합니다. ${command.rate}" }
        require ( command.fraudRatio in  0.0..1.0) { "Fraud Ratio는 누구 보다 열심히"}

    }
}
