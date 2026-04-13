package com.gijun.logdetect.generator.application.port.`in`.query

import com.gijun.logdetect.generator.domain.model.GeneratorStatus

interface GetGeneratorStatusUseCase {
    fun getGeneratorStatus(): GeneratorStatus
}
