package com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.generator.application.dto.command.CreateScenarioCommand
import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive

data class CreateScenarioRequest(
    @field:NotBlank
    val name: String,

    @field:NotNull
    val type: RequestType,

    @field:NotNull
    val attackType: AttackType,

    @field:NotNull
    val successful: Boolean,

    @field:Positive
    val rate: Long,

    @field:Positive
    val fraudRatio: Long,
) {
    fun toCommand() = CreateScenarioCommand(
        name = name,
        type = type,
        attackType = attackType,
        successful = successful,
        rate = rate,
        fraudRatio = fraudRatio,
    )
}
