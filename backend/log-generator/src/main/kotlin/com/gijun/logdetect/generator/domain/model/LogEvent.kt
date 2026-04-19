package com.gijun.logdetect.generator.domain.model

import java.time.Instant

data class LogEvent(
    val id: Long?,
    val transactionId: String,
    val source: String,
    val level: String,
    val message: String,
    val timestamp: Instant? = Instant.now(),
    val host: String?,
    val ip: String?,
    val userId: Long?,
    val attributes: Map<String, String>? = emptyMap(),
)
