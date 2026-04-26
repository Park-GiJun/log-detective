package com.gijun.logdetect.ingest.infrastructure.scheduler

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.model.Outbox
import com.gijun.logdetect.ingest.infrastructure.util.ErrorRedactor
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
        // 미지원 채널은 try 진입 전 가드 — 재시도 의미 없음, 즉시 dead 처리.
        if (outbox.channel !in SUPPORTED_CHANNELS) {
            outboxPersistencePort.markDead(id, "unsupported channel: ${outbox.channel}")
            return false
        }
        return try {
            val event = objectMapper.readValue(outbox.payload, LogEvent::class.java)
            when (outbox.channel) {
                ChannelType.ES -> logEventSearchPort.index(event)
                ChannelType.KAFKA -> logEventMessagePort.publishRaw(event)
                ChannelType.FILE, ChannelType.OTHERS -> Unit // SUPPORTED_CHANNELS 가드로 도달 불가
            }
            outboxPersistencePort.markPublished(id)
            true
        } catch (e: JsonProcessingException) {
            // 페이로드 손상은 영구 실패 — 5회 재시도해도 동일 결과이므로 즉시 dead 처리.
            val errorMsg = ErrorRedactor.redact(e.message ?: "payload corrupted").take(MAX_ERROR_LENGTH)
            outboxPersistencePort.markDead(id, "payload corrupted: $errorMsg")
            false
        } catch (e: Exception) {
            handleFailure(outbox, e)
            false
        }
    }

    private fun handleFailure(outbox: Outbox, e: Exception) {
        val id = outbox.id ?: return
        // 외부 시스템 예외에 호스트/자격 증명/토큰이 노출될 수 있으므로 redact 후 저장.
        val raw = e.message ?: e.javaClass.simpleName
        val error = ErrorRedactor.redact(raw).take(MAX_ERROR_LENGTH)
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
        // 백오프: nextAttempt(1..5) → 5s × 2^n. attempts=1 → 10s, =2 → 20s, =3 → 40s, =4 → 80s, =5 → 160s.
        // MAX_BACKOFF_SHIFT=6 으로 5분(320s) 상한 — 룰별 SLA 참고: doc/design/design.md §13 (R001 5분 윈도우 영향, 이슈 #50).
        private const val BASE_BACKOFF_SEC = 5L
        private const val MAX_BACKOFF_SHIFT = 6
        private const val MAX_ERROR_LENGTH = 1000
        private val SUPPORTED_CHANNELS = setOf(ChannelType.ES, ChannelType.KAFKA)
    }
}
