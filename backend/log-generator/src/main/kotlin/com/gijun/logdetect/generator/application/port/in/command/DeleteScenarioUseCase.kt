package com.gijun.logdetect.generator.application.port.`in`.command

import com.gijun.logdetect.generator.application.dto.command.DeleteScenarioCommand

interface DeleteScenarioUseCase {
    fun deleteScenario(command: DeleteScenarioCommand)
}
