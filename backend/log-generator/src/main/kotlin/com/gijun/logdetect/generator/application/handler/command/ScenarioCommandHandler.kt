package com.gijun.logdetect.generator.application.handler.command

import com.gijun.logdetect.generator.application.dto.command.CreateScenarioCommand
import com.gijun.logdetect.generator.application.dto.command.DeleteScenarioCommand
import com.gijun.logdetect.generator.application.dto.result.ScenarioResult
import com.gijun.logdetect.generator.application.port.`in`.command.CreateScenarioUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.DeleteScenarioUseCase
import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort
import com.gijun.logdetect.generator.domain.model.Scenario
import org.springframework.transaction.annotation.Transactional

open class ScenarioCommandHandler(
    private val scenarioPersistencePort: ScenarioPersistencePort,
) : CreateScenarioUseCase, DeleteScenarioUseCase {

    @Transactional
    override fun createScenario(command: CreateScenarioCommand): ScenarioResult {
        val scenario = Scenario(
            id = null,
            name = command.name,
            type = command.type,
            attackType = command.attackType,
            successful = command.successful,
            rate = command.rate,
            fraudRatio = command.fraudRatio,
        )
        return ScenarioResult.from(scenarioPersistencePort.save(scenario))
    }

    @Transactional
    override fun deleteScenario(command: DeleteScenarioCommand) {
        requireNotNull(scenarioPersistencePort.findById(command.id)) {
            "시나리오를 찾을 수 없습니다: id=${command.id}"
        }
        scenarioPersistencePort.deleteById(command.id)
    }
}
