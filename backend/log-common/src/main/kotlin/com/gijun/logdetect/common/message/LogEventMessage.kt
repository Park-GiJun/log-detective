package com.gijun.logdetect.common.message

import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.common.domain.enums.LogLevel
import java.time.Instant
import java.util.UUID

data class LogEventMessage(
    val eventId: UUID,
    val source: String,
    val level: LogLevel,
    val message: String,
    val timestamp: Instant,
    val host: String? = null,
    val ip: String? = null,
    val userId: String? = null,
    val attributes: Map<String, String> = emptyMap(),
) {
    fun toDomain(): LogEvent =
        LogEvent(
            eventId = eventId,
            source = source,
            level = level,
            message = message,
            timestamp = timestamp,
            host = host,
            ip = ip,
            userId = userId,
            attributes = attributes,
        )

    companion object {
        fun from(event: LogEvent): LogEventMessage =
            LogEventMessage(
                eventId = event.eventId,
                source = event.source,
                level = event.level,
                message = event.message,
                timestamp = event.timestamp,
                host = event.host,
                ip = event.ip,
                userId = event.userId,
                attributes = event.attributes,
            )
    }
}
