package com.gijun.logdetect.generator.domain.model

import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType

data class Scenario(
    val id: Long?,
    val name: String,
    val type: RequestType,
    val attackType: AttackType,
    val successful: Boolean,
    val rate: Long,
    val fraudRatio: Long
)
