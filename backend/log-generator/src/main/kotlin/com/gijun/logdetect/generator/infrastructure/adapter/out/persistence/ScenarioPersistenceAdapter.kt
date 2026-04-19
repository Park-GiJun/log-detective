package com.gijun.logdetect.generator.infrastructure.adapter.out.persistence

import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort
import com.gijun.logdetect.generator.domain.model.Scenario
import com.gijun.logdetect.generator.infrastructure.adapter.out.persistence.entity.ScenarioEntity
import com.gijun.logdetect.generator.infrastructure.adapter.out.persistence.repository.ScenarioRepository
import org.springframework.stereotype.Component

@Component
class ScenarioPersistenceAdapter(
    private val scenarioRepository: ScenarioRepository,
) : ScenarioPersistencePort {

    override fun save(scenario: Scenario): Scenario =
        scenarioRepository.save(ScenarioEntity.from(scenario)).toDomain()

    override fun findById(id: Long): Scenario? =
        scenarioRepository.findById(id).orElse(null)?.toDomain()

    override fun findAll(): List<Scenario> =
        scenarioRepository.findAll().map { it.toDomain() }

    override fun deleteById(id: Long) =
        scenarioRepository.deleteById(id)
}
