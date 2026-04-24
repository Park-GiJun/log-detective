package com.gijun.logdetect.ingest.infrastructure.adapter.out.messaging

import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.common.message.LogEventMessage
import com.gijun.logdetect.common.topic.KafkaTopics
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class LogEventMessageAdapter(
    private val kafkaTemplate: KafkaTemplate<String, LogEventMessage>,
) : LogEventMessagePort {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun publishRaw(event: LogEvent) {
        val message = LogEventMessage.from(event)
        // KafkaTemplate 는 CompletableFuture 를 반환 — suspend 로 브리지
        kafkaTemplate.send(KafkaTopics.LOGS_RAW, event.eventId.toString(), message).await()
        logger.debug("Kafka 발행 — topic: {}, eventId: {}", KafkaTopics.LOGS_RAW, event.eventId)
    }

    override suspend fun publishRawBatch(events: List<LogEvent>) {
        events.forEach { publishRaw(it) }
    }
}
