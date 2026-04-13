package com.gijun.logdetect.generator.application.dto.command

data class GeneratorStartCommand(
    val rate: Int,
    val fraudRatio: Double
)
