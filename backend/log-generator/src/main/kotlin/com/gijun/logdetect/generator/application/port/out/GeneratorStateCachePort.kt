package com.gijun.logdetect.generator.application.port.out

import com.gijun.logdetect.generator.domain.model.GeneratorStatus

interface GeneratorStateCachePort {

    fun markRunning(scenarioId: Long, rate: Int)

    fun markStopped(scenarioId: Long)

    fun incrementSent(scenarioId: Long): Long

    fun incrementFailed(scenarioId: Long): Long

    fun resetCounters(scenarioId: Long)

    fun getStatus(scenarioId: Long): GeneratorStatus

    fun getActiveScenarioIds(): Set<Long>

    fun getAllStatuses(): List<GeneratorStatus>
}
