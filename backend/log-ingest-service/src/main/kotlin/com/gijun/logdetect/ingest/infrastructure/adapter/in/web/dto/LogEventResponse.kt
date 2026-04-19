package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.ingest.application.dto.result.LogEventResult
import java.time.Instant
import java.util.UUID

data class LogEventResponse(
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
        fun from(result: LogEventResult): LogEventResponse = LogEventResponse(
            eventId = result.eventId,
            source = result.source,
            level = result.level,
            message = result.message,
            timestamp = result.timestamp,
            host = result.host,
            ip = result.ip,
            userId = result.userId,
            attributes = result.attributes,
            ingestedAt = result.ingestedAt,
        )
    }
}
