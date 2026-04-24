package com.gijun.logdetect.generator.application.port.out

import com.gijun.logdetect.generator.domain.model.Scenario

interface ScenarioPersistencePort {
    suspend fun save(scenario: Scenario): Scenario
    suspend fun findById(id: Long): Scenario?
    suspend fun findAll(): List<Scenario>
    suspend fun deleteById(id: Long)
}
