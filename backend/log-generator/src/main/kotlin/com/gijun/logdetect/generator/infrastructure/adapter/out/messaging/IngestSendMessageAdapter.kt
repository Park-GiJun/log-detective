package com.gijun.logdetect.generator.infrastructure.adapter.out.messaging

import com.gijun.logdetect.generator.application.port.out.IngestSendMessagePort
import com.gijun.logdetect.generator.domain.model.LogEvent
import com.gijun.logdetect.generator.infrastructure.adapter.out.messaging.dto.IngestSendMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class IngestSendMessageAdapter(
    private val kafkaTemplate: KafkaTemplate<String, IngestSendMessage>,
    @Value("\${generator.kafka.topic}") private val topic: String,
) : IngestSendMessagePort {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun send(log: LogEvent): Boolean {
        return try {
            val message = IngestSendMessage.from(log)
            withContext(Dispatchers.IO) {
                kafkaTemplate.send(topic, log.transactionId, message).get()
            }
            true
        } catch (e: Exception) {
            logger.error("Kafka 전송 실패 — transactionId: {}", log.transactionId, e)
            false
        }
    }
}
