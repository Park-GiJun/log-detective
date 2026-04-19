package com.gijun.logdetect.generator.infrastructure.adapter.out.persistence.repository

import com.gijun.logdetect.generator.infrastructure.adapter.out.persistence.entity.ScenarioEntity
import org.springframework.data.jpa.repository.JpaRepository

interface ScenarioRepository : JpaRepository<ScenarioEntity, Long>
