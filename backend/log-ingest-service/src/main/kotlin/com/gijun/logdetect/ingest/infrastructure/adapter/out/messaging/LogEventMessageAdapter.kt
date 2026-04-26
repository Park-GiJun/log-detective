package com.gijun.logdetect.ingest.infrastructure.adapter.out.messaging

import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.common.message.LogEventMessage
import com.gijun.logdetect.common.topic.KafkaTopics
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class LogEventMessageAdapter(
    private val kafkaTemplate: KafkaTemplate<String, LogEventMessage>,
) : LogEventMessagePort {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    /**
     * Kafka 발행은 broker ack 까지 동기 wait — Outbox markPublished 가
     * "broker 에 도달했다" 의 의미를 가지도록 보장한다.
     * 타임아웃/실패 시 RuntimeException 을 던져 OutboxPublisher.handleFailure
     * 경로로 넘기고, markFailed 가 처리하도록 위임한다.
     */
    override fun publishRaw(event: LogEvent) {
        val message = LogEventMessage.from(event)
        try {
            kafkaTemplate
                .send(KafkaTopics.LOGS_RAW, event.eventId.toString(), message)
                .get(SEND_TIMEOUT_SEC, TimeUnit.SECONDS)
            logger.debug("Kafka 발행 완료 — topic: {}, eventId: {}", KafkaTopics.LOGS_RAW, event.eventId)
        } catch (e: Exception) {
            // ack 미확인 → 상위 catch (OutboxPublisher) 에서 markFailed 처리해야 데이터 유실을 방지할 수 있다.
            throw RuntimeException(
                "Kafka 발행 실패 — topic: ${KafkaTopics.LOGS_RAW}, eventId: ${event.eventId}, cause: ${e.message}",
                e,
            )
        }
    }

    override fun publishRawBatch(events: List<LogEvent>) {
        // 각 이벤트를 publishRaw 로 위임 — 부분 실패 시 예외가 즉시 전파되어
        // 이미 ack 된 분만 markPublished 되고 실패 이후는 OutboxPublisher 가 재시도한다.
        events.forEach { publishRaw(it) }
    }

    companion object {
        private const val SEND_TIMEOUT_SEC = 10L
    }
}
