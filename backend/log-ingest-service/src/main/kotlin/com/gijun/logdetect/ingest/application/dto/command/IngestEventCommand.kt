package com.gijun.logdetect.ingest.application.dto.command

import java.time.Instant

data class IngestEventCommand(
    val source: String,
    val level: String,
    val message: String,
    val timestamp: Instant,
    val host: String? = null,
    val ip: String? = null,
    val userId: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)
