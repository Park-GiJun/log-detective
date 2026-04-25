package com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.generator.application.dto.command.CreateScenarioCommand
import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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

    // EPS (events per second). Generator 가 .toInt() 로 사용.
    @field:Positive
    val rate: Long,

    // 사기 비율 (0~100 퍼센트). Generator 가 / 100.0 으로 변환.
    @field:Min(0)
    @field:Max(100)
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
