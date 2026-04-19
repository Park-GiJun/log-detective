package com.gijun.logdetect.generator.application.port.`in`.command

import com.gijun.logdetect.generator.application.dto.command.CreateScenarioCommand
import com.gijun.logdetect.generator.application.dto.result.ScenarioResult

interface CreateScenarioUseCase {
    fun createScenario(command: CreateScenarioCommand): ScenarioResult
}
