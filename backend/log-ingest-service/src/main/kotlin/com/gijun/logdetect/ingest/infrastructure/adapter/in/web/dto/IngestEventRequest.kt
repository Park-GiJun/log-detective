package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.ingest.application.dto.command.IngestEventCommand
import java.time.Instant

data class IngestEventRequest(
    val source: String,
    val level: String,
    val message: String,
    val timestamp: Instant? = null,
    val host: String? = null,
    val ip: String? = null,
    val userId: String? = null,
    val attributes: Map<String, String>? = null,
) {
    fun toCommand(): IngestEventCommand = IngestEventCommand(
        source = source,
        level = level,
        message = message,
        timestamp = timestamp ?: Instant.now(),
        host = host,
        ip = ip,
        userId = userId,
        attributes = attributes ?: emptyMap(),
    )
}
