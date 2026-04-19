package com.gijun.logdetect.web.api

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Mock API Client — 백엔드 없이 프론트엔드 개발용.
 * 실제 백엔드 연결 시 Ktor HttpClient 구현으로 교체.
 */
object ApiClient {
    private var running = false
    private var totalSent = 0L
    private var totalFailed = 0L
    private var configuredRate = 0
    private var nextId = 4L

    private val scenarios = mutableListOf(
        ScenarioResponse(1, "Brute Force Test", RequestType.REST, AttackType.BRUTE_FORCE, false, 50, 10),
        ScenarioResponse(2, "SQL Injection Scan", RequestType.REST, AttackType.SQL_INJECTION, false, 30, 5),
        ScenarioResponse(3, "Normal Traffic", RequestType.KAFKA, AttackType.ERROR_SPIKE, true, 100, 20),
    )

    // ── Generator ──────────────────────────────────────────────────────

    suspend fun getGeneratorStatus(): Result<GeneratorStatusResponse> {
        delay(80)
        if (running) {
            totalSent += Random.nextLong(configuredRate.toLong(), configuredRate * 2L)
            if (Random.nextFloat() < 0.05f) totalFailed += Random.nextLong(1, 5)
        }
        return Result.success(
            GeneratorStatusResponse(
                running = running,
                totalSent = totalSent,
                totalFailed = totalFailed,
                configuredRate = configuredRate,
            ),
        )
    }

    suspend fun startGenerator(rate: Int, fraudRatio: Double): Result<Unit> {
        delay(200)
        running = true
        configuredRate = rate
        return Result.success(Unit)
    }

    suspend fun stopGenerator(): Result<Unit> {
        delay(200)
        running = false
        configuredRate = 0
        return Result.success(Unit)
    }

    suspend fun burstGenerator(rate: Int, fraudRatio: Double): Result<Unit> {
        delay(200)
        totalSent += rate * 10
        if (Random.nextFloat() < 0.1f) totalFailed += Random.nextLong(1, 10)
        return Result.success(Unit)
    }

    // ── Scenario ───────────────────────────────────────────────────────

    suspend fun getScenarios(): Result<List<ScenarioResponse>> {
        delay(100)
        return Result.success(scenarios.toList())
    }

    suspend fun createScenario(request: CreateScenarioRequest): Result<ScenarioResponse> {
        delay(200)
        val response = ScenarioResponse(
            id = nextId++,
            name = request.name,
            type = request.type,
            attackType = request.attackType,
            successful = request.successful,
            rate = request.rate,
            fraudRatio = request.fraudRatio,
        )
        scenarios.add(response)
        return Result.success(response)
    }

    suspend fun deleteScenario(id: Long): Result<Unit> {
        delay(150)
        scenarios.removeAll { it.id == id }
        return Result.success(Unit)
    }
}
