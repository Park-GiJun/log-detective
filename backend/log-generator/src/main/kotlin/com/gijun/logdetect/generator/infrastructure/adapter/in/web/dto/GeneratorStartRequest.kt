package com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.generator.application.dto.command.GeneratorStartCommand
import jakarta.validation.constraints.Positive

data class GeneratorStartRequest(
    @field:Positive
    val scenarioId: Long,
) {
    fun toCommand() = GeneratorStartCommand(scenarioId = scenarioId)
}
