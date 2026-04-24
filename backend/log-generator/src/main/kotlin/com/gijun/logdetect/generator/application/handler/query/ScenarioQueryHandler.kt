package com.gijun.logdetect.generator.application.handler.query

import com.gijun.logdetect.generator.application.dto.query.GetScenarioQuery
import com.gijun.logdetect.generator.application.dto.result.ScenarioResult
import com.gijun.logdetect.generator.application.port.`in`.query.GetScenarioUseCase
import com.gijun.logdetect.generator.application.port.`in`.query.GetScenariosUseCase
import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort

class ScenarioQueryHandler(
    private val scenarioPersistencePort: ScenarioPersistencePort,
) : GetScenarioUseCase, GetScenariosUseCase {

    override suspend fun getScenario(query: GetScenarioQuery): ScenarioResult {
        val scenario = requireNotNull(scenarioPersistencePort.findById(query.id)) {
            "시나리오를 찾을 수 없습니다: id=${query.id}"
        }
        return ScenarioResult.from(scenario)
    }

    override suspend fun getScenarios(): List<ScenarioResult> =
        scenarioPersistencePort.findAll().map { ScenarioResult.from(it) }
}
