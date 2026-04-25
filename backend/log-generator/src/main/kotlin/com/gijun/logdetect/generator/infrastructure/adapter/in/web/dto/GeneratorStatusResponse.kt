package com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.generator.domain.model.GeneratorStatus

data class GeneratorStatusResponse(
    val scenarioId: Long,
    val running: Boolean,
    val totalSent: Long,
    val totalFailed: Long,
    val configuredRate: Int,
) {
    companion object {
        fun from(status: GeneratorStatus) = GeneratorStatusResponse(
            scenarioId = status.scenarioId,
            running = status.running,
            totalSent = status.totalSent,
            totalFailed = status.totalFailed,
            configuredRate = status.configuredRate,
        )
    }
}
