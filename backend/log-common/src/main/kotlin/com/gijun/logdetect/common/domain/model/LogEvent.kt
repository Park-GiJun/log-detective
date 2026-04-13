package com.gijun.logdetect.common.domain.model

import com.gijun.logdetect.common.domain.enums.LogLevel
import java.time.Instant
import java.util.UUID

data class LogEvent(
    val eventId: UUID,
    val source: String,
    val level: LogLevel,
    val message: String,
    val timestamp: Instant,
    val host: String? = null,
    val ip: String? = null,
    val userId: String? = null,
    val attributes: Map<String, String> = emptyMap(),
)
