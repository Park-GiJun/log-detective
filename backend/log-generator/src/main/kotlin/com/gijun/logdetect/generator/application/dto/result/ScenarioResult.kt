package com.gijun.logdetect.generator.application.dto.result

import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType
import com.gijun.logdetect.generator.domain.model.Scenario

data class ScenarioResult(
    val id: Long,
    val name: String,
    val type: RequestType,
    val attackType: AttackType,
    val successful: Boolean,
    val rate: Long,
    val fraudRatio: Long,
) {
    companion object {
        fun from(scenario: Scenario) = ScenarioResult(
            id = scenario.id!!,
            name = scenario.name,
            type = scenario.type,
            attackType = scenario.attackType,
            successful = scenario.successful,
            rate = scenario.rate,
            fraudRatio = scenario.fraudRatio,
        )
    }
}
