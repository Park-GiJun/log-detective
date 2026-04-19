package com.gijun.logdetect.generator.application.dto.command

import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType

data class CreateScenarioCommand(
    val name: String,
    val type: RequestType,
    val attackType: AttackType,
    val successful: Boolean,
    val rate: Long,
    val fraudRatio: Long,
)
