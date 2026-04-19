package com.gijun.logdetect.generator.application.handler.query

import com.gijun.logdetect.generator.application.handler.command.GeneratorCommandHandler
import com.gijun.logdetect.generator.application.port.`in`.query.GetGeneratorStatusUseCase
import com.gijun.logdetect.generator.domain.model.GeneratorStatus

class GeneratorQueryHandler(
    private val commandHandler: GeneratorCommandHandler,
) : GetGeneratorStatusUseCase {

    override fun getGeneratorStatus(): GeneratorStatus = GeneratorStatus(
        running = commandHandler.isRunning(),
        totalSent = commandHandler.totalSent.get(),
        totalFailed = commandHandler.totalFailed.get(),
        configuredRate = commandHandler.currentRate.get(),
    )
}
