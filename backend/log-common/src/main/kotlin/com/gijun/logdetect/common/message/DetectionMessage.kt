package com.gijun.logdetect.common.message

import com.gijun.logdetect.common.domain.model.Detection
import com.gijun.logdetect.common.domain.enums.Severity
import java.time.Instant
import java.util.UUID

data class DetectionMessage(
    val detectionId: UUID,
    val ruleId: String,
    val ruleName: String,
    val severity: Severity,
    val eventId: UUID,
    val source: String,
    val reason: String,
    val evidence: Map<String, String> = emptyMap(),
    val detectedAt: Instant,
) {
    fun toDomain(): Detection =
        Detection(
            detectionId = detectionId,
            ruleId = ruleId,
            ruleName = ruleName,
            severity = severity,
            eventId = eventId,
            source = source,
            reason = reason,
            evidence = evidence,
            detectedAt = detectedAt,
        )

    companion object {
        fun from(detection: Detection): DetectionMessage =
            DetectionMessage(
                detectionId = detection.detectionId,
                ruleId = detection.ruleId,
                ruleName = detection.ruleName,
                severity = detection.severity,
                eventId = detection.eventId,
                source = detection.source,
                reason = detection.reason,
                evidence = detection.evidence,
                detectedAt = detection.detectedAt,
            )
    }
}
