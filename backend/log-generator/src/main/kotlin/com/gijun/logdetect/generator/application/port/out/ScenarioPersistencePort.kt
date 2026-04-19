package com.gijun.logdetect.generator.application.port.out

import com.gijun.logdetect.generator.domain.model.Scenario

interface ScenarioPersistencePort {
    fun save(scenario: Scenario): Scenario
    fun findById(id: Long): Scenario?
    fun findAll(): List<Scenario>
    fun deleteById(id: Long)
}
