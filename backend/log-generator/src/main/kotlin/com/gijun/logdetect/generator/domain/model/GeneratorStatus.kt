package com.gijun.logdetect.generator.domain.model

data class GeneratorStatus(
    val running: Boolean,
    val totalSent: Long,
    val totalFailed: Long,
    val configuredRate: Long
)
