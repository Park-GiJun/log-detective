package com.gijun.logdetect.ingest.infrastructure.adapter.out.messaging

import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.common.message.LogEventMessage
import com.gijun.logdetect.common.topic.KafkaTopics
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class LogEventMessageAdapter(
    private val kafkaTemplate: KafkaTemplate<String, LogEventMessage>,
) : LogEventMessagePort {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun publishRaw(event: LogEvent) {
        val message = LogEventMessage.from(event)
        kafkaTemplate.send(KafkaTopics.LOGS_RAW, event.eventId.toString(), message)
        logger.debug("Kafka 발행 — topic: {}, eventId: {}", KafkaTopics.LOGS_RAW, event.eventId)
    }

    override fun publishRawBatch(events: List<LogEvent>) {
        events.forEach { publishRaw(it) }
    }
}
