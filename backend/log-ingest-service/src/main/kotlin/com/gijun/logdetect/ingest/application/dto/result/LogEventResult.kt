package com.gijun.logdetect.ingest.application.dto.result

import com.gijun.logdetect.common.domain.model.LogEvent
import java.time.Instant
import java.util.UUID

data class LogEventResult(
    val eventId: UUID,
    val source: String,
    val level: String,
    val message: String,
    val timestamp: Instant,
    val host: String?,
    val ip: String?,
    val userId: String?,
    val attributes: Map<String, String>,
    val ingestedAt: Instant?,
) {
    companion object {
        fun from(event: LogEvent, ingestedAt: Instant? = null): LogEventResult =
            LogEventResult(
                eventId = event.eventId,
                source = event.source,
                level = event.level.name,
                message = event.message,
                timestamp = event.timestamp,
                host = event.host,
                ip = event.ip,
                userId = event.userId,
                attributes = event.attributes,
                ingestedAt = ingestedAt,
            )
    }
}
