package com.gijun.logdetect.ingest.infrastructure.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.model.Outbox
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class OutboxPublisher(
    private val outboxPersistencePort: OutboxPersistencePort,
    private val logEventSearchPort: LogEventSearchPort,
    private val logEventMessagePort: LogEventMessagePort,
    private val objectMapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Scheduled(fixedDelayString = "\${logdetect.outbox.poll-interval-ms:1000}")
    @Transactional
    fun pollAndDispatch() {
        val pending = outboxPersistencePort.fetchPending(BATCH_SIZE)
        if (pending.isEmpty()) return

        var success = 0
        var failed = 0
        pending.forEach {
            if (dispatch(it)) success++ else failed++
        }
        logger.debug("Outbox dispatch — total: {}, success: {}, failed: {}", pending.size, success, failed)
    }

    private fun dispatch(outbox: Outbox): Boolean {
        val id = outbox.id ?: return false
        return try {
            val event = objectMapper.readValue(outbox.payload, LogEvent::class.java)
            when (outbox.channel) {
                ChannelType.ES -> logEventSearchPort.index(event)
                ChannelType.KAFKA -> logEventMessagePort.publishRaw(event)
                ChannelType.FILE, ChannelType.OTHERS -> {
                    outboxPersistencePort.markDead(id, "unsupported channel: ${outbox.channel}")
                    return false
                }
            }
            outboxPersistencePort.markPublished(id)
            true
        } catch (e: Exception) {
            handleFailure(outbox, e)
            false
        }
    }

    private fun handleFailure(outbox: Outbox, e: Exception) {
        val id = outbox.id ?: return
        val error = e.message?.take(MAX_ERROR_LENGTH) ?: e.javaClass.simpleName
        val nextAttempts = outbox.attempts + 1
        if (nextAttempts >= MAX_ATTEMPTS) {
            logger.warn("Outbox 최대 시도 초과 — id: {}, attempts: {}", id, nextAttempts, e)
            outboxPersistencePort.markDead(id, error)
            return
        }
        val backoffSec = BASE_BACKOFF_SEC shl nextAttempts.coerceAtMost(MAX_BACKOFF_SHIFT)
        outboxPersistencePort.markFailed(id, error, Instant.now().plusSeconds(backoffSec))
    }

    companion object {
        private const val BATCH_SIZE = 100
        private const val MAX_ATTEMPTS = 5
        private const val BASE_BACKOFF_SEC = 5L
        private const val MAX_BACKOFF_SHIFT = 6
        private const val MAX_ERROR_LENGTH = 1000
    }
}
