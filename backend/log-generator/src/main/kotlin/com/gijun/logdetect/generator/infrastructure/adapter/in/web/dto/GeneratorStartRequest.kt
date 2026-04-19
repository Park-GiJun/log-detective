package com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.generator.application.dto.command.GeneratorStartCommand
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive

data class GeneratorStartRequest(
    @field:Positive
    val rate: Int,

    @field:Min(0)
    @field:Max(1)
    val fraudRatio: Double,
) {
    fun toCommand() = GeneratorStartCommand(
        rate = rate,
        fraudRatio = fraudRatio,
    )
}
