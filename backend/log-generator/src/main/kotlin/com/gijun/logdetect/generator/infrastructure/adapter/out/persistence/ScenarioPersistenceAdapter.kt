package com.gijun.logdetect.generator.infrastructure.adapter.out.persistence

import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort
import com.gijun.logdetect.generator.domain.model.Scenario
import com.gijun.logdetect.generator.infrastructure.adapter.out.persistence.table.ScenariosTable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update

class ScenarioPersistenceAdapter(
    private val database: R2dbcDatabase,
) : ScenarioPersistencePort {

    override suspend fun save(scenario: Scenario): Scenario = suspendTransaction(db = database) {
        if (scenario.id == null) {
            val inserted = ScenariosTable.insert { row ->
                row[name] = scenario.name
                row[type] = scenario.type
                row[attackType] = scenario.attackType
                row[successful] = scenario.successful
                row[rate] = scenario.rate
                row[fraudRatio] = scenario.fraudRatio
            }
            scenario.copy(id = inserted[ScenariosTable.id])
        } else {
            ScenariosTable.update({ ScenariosTable.id eq scenario.id }) { row ->
                row[name] = scenario.name
                row[type] = scenario.type
                row[attackType] = scenario.attackType
                row[successful] = scenario.successful
                row[rate] = scenario.rate
                row[fraudRatio] = scenario.fraudRatio
            }
            scenario
        }
    }

    override suspend fun findById(id: Long): Scenario? = suspendTransaction(db = database) {
        ScenariosTable.selectAll()
            .where { ScenariosTable.id eq id }
            .map { it.toDomain() }
            .toList()
            .firstOrNull()
    }

    override suspend fun findAll(): List<Scenario> = suspendTransaction(db = database) {
        ScenariosTable.selectAll()
            .map { it.toDomain() }
            .toList()
    }

    override suspend fun deleteById(id: Long) {
        suspendTransaction(db = database) {
            ScenariosTable.deleteWhere { ScenariosTable.id eq id }
        }
    }

    private fun ResultRow.toDomain(): Scenario = Scenario(
        id = this[ScenariosTable.id],
        name = this[ScenariosTable.name],
        type = this[ScenariosTable.type],
        attackType = this[ScenariosTable.attackType],
        successful = this[ScenariosTable.successful],
        rate = this[ScenariosTable.rate],
        fraudRatio = this[ScenariosTable.fraudRatio],
    )
}
