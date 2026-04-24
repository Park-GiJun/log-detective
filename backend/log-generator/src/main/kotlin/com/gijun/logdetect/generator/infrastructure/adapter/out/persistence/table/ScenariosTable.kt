package com.gijun.logdetect.generator.infrastructure.adapter.out.persistence.table

import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType
import org.jetbrains.exposed.v1.core.Table

object ScenariosTable : Table("generator.scenario") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 255)
    val type = enumerationByName("type", 64, RequestType::class)
    val attackType = enumerationByName("attack_type", 64, AttackType::class)
    val successful = bool("successful")
    val rate = long("rate")
    val fraudRatio = long("fraud_ratio")

    override val primaryKey = PrimaryKey(id)
}
