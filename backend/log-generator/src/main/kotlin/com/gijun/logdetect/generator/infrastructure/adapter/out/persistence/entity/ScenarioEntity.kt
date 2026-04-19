package com.gijun.logdetect.generator.infrastructure.adapter.out.persistence.entity

import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType
import com.gijun.logdetect.generator.domain.model.Scenario
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "scenario")
class ScenarioEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: RequestType,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val attackType: AttackType,

    @Column(nullable = false)
    val successful: Boolean,

    @Column(nullable = false)
    val rate: Long,

    @Column(nullable = false)
    val fraudRatio: Long,
) {
    fun toDomain(): Scenario = Scenario(
        id = id,
        name = name,
        type = type,
        attackType = attackType,
        successful = successful,
        rate = rate,
        fraudRatio = fraudRatio,
    )

    companion object {
        fun from(scenario: Scenario) = ScenarioEntity(
            id = scenario.id,
            name = scenario.name,
            type = scenario.type,
            attackType = scenario.attackType,
            successful = scenario.successful,
            rate = scenario.rate,
            fraudRatio = scenario.fraudRatio,
        )
    }
}
