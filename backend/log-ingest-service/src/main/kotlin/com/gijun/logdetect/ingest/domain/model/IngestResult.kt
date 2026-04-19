package com.gijun.logdetect.ingest.domain.model

import com.gijun.logdetect.common.domain.enums.Severity
import java.time.Instant

data class IngestResult(
    val ingestId: Long?,
    val transactionId: String,
    val riskLevel: Severity,
    val riskScore: Double,
    val triggeredRules: List<String>,
    val detectedAt: Instant,
) {
    init {
        require(riskScore in 0.0..MAX_RISK_SCORE) {
            "riskScore는 0~$MAX_RISK_SCORE 사이여야 합니다: $riskScore"
        }
    }

    companion object {
        const val MAX_RISK_SCORE: Double = 100.0
    }
}
