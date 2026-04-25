package com.gijun.logdetect.web.api

import kotlinx.serialization.Serializable

@Serializable
data class GeneratorStatusResponse(
    val scenarioId: Long,
    val running: Boolean = false,
    val totalSent: Long = 0,
    val totalFailed: Long = 0,
    val configuredRate: Int = 0,
)

@Serializable
data class GeneratorStartRequest(
    val scenarioId: Long,
)

@Serializable
enum class RequestType {
    KAFKA,
    FILE,
    REST,
}

@Serializable
enum class AttackType {
    BRUTE_FORCE,
    SQL_INJECTION,
    ERROR_SPIKE,
    OFF_HOUR_ACCESS,
    GEO_ANOMALY,
    RARE_EVENT,
}

@Serializable
data class ScenarioResponse(
    val id: Long,
    val name: String,
    val type: RequestType,
    val attackType: AttackType,
    val successful: Boolean,
    val rate: Long,
    val fraudRatio: Long,
)

@Serializable
data class CreateScenarioRequest(
    val name: String,
    val type: RequestType,
    val attackType: AttackType,
    val successful: Boolean,
    val rate: Long,
    val fraudRatio: Long,
)
