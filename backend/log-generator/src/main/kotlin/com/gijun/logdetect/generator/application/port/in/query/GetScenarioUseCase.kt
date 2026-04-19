package com.gijun.logdetect.generator.application.port.`in`.query

import com.gijun.logdetect.generator.application.dto.query.GetScenarioQuery
import com.gijun.logdetect.generator.application.dto.result.ScenarioResult

interface GetScenarioUseCase {
    fun getScenario(query: GetScenarioQuery): ScenarioResult
}
