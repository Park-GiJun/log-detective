package com.gijun.logdetect.generator.application.port.`in`.query

import com.gijun.logdetect.generator.application.dto.result.ScenarioResult

interface GetScenariosUseCase {
    fun getScenarios(): List<ScenarioResult>
}
