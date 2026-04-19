package com.gijun.logdetect.generator.application.port.out

import com.gijun.logdetect.generator.domain.model.GeneratorStatus

interface GeneratorStateCachePort {

    fun markRunning(rate: Int)

    fun markStopped()

    fun incrementSent(): Long

    fun incrementFailed(): Long

    fun resetCounters()

    fun getStatus(): GeneratorStatus
}
