package com.gijun.logdetect.generator.application.port.`in`.command

import com.gijun.logdetect.generator.application.dto.command.GeneratorStartCommand

interface BurstGeneratorUseCase {
    fun burstGenerator(command : GeneratorStartCommand)
}
