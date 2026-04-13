package com.gijun.logdetect.common.domain.model

import com.gijun.logdetect.common.domain.enums.Severity
import java.time.Instant
import java.util.UUID

data class Detection(
    val detectionId: UUID,
    val ruleId: String,
    val ruleName: String,
    val severity: Severity,
    val eventId: UUID,
    val source: String,
    val reason: String,
    val evidence: Map<String, String> = emptyMap(),
    val detectedAt: Instant,
)
