package com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.generator.application.dto.result.ScenarioResult
import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType

data class ScenarioResponse(
    val id: Long,
    val name: String,
    val type: RequestType,
    val attackType: AttackType,
    val successful: Boolean,
    val rate: Long,
    val fraudRatio: Long,
) {
    companion object {
        fun from(result: ScenarioResult) = ScenarioResponse(
            id = result.id,
            name = result.name,
            type = result.type,
            attackType = result.attackType,
            successful = result.successful,
            rate = result.rate,
            fraudRatio = result.fraudRatio,
        )
    }
}
