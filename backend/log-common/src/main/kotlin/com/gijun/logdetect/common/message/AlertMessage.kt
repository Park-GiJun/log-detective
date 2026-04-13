package com.gijun.logdetect.common.message

import com.gijun.logdetect.common.domain.enums.AlertStatus
import com.gijun.logdetect.common.domain.enums.Severity
import java.time.Instant
import java.util.UUID

data class AlertMessage(
    val alertId: UUID,
    val fingerprint: String,
    val ruleId: String,
    val severity: Severity,
    val summary: String,
    val firstSeenAt: Instant,
    val lastSeenAt: Instant,
    val hitCount: Int,
    val status: AlertStatus = AlertStatus.OPEN,
    val detectionIds: List<UUID> = emptyList(),
    val attributes: Map<String, String> = emptyMap(),
)
