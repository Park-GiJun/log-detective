package com.gijun.logdetect.generator.application.port.`in`.command

import com.gijun.logdetect.generator.application.dto.command.GeneratorStartCommand

interface StartGeneratorUseCase {
    fun startGenerator(command : GeneratorStartCommand)
}
