package com.gijun.logdetect.generator.infrastructure.adapter.out.messaging.dto

import com.gijun.logdetect.generator.domain.model.LogEvent
import java.time.Instant

data class IngestSendMessage(
    val transactionId: String,
    val source: String,
    val level: String,
    val message: String,
    val timestamp: Instant,
    val host: String,
    val ip: String,
    val userId: Long,
    val attributes: Map<String, String>,
) {
    companion object {
        fun from(event: LogEvent) = IngestSendMessage(
            transactionId = event.transactionId,
            source = event.source,
            level = event.level,
            message = event.message,
            timestamp = event.timestamp ?: Instant.now(),
            host = event.host.orEmpty(),
            ip = event.ip.orEmpty(),
            userId = event.userId ?: 0L,
            attributes = event.attributes.orEmpty(),
        )
    }
}
