package com.gijun.logdetect.generator.application.handler.query

import com.gijun.logdetect.generator.application.port.`in`.query.GetGeneratorStatusUseCase
import com.gijun.logdetect.generator.application.port.out.GeneratorStateCachePort
import com.gijun.logdetect.generator.domain.model.GeneratorStatus

class GeneratorQueryHandler(
    private val generatorStateCachePort: GeneratorStateCachePort,
) : GetGeneratorStatusUseCase {

    override fun getGeneratorStatus(): GeneratorStatus =
        generatorStateCachePort.getStatus()
}
