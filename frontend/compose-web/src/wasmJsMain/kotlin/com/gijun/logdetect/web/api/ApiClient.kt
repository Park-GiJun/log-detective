package com.gijun.logdetect.web.api

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Mock API Client — 백엔드 없이 프론트엔드 개발용.
 * 실제 백엔드 연결 시 Ktor HttpClient 구현으로 교체.
 *
 * 다중 시나리오 동시 실행을 흉내내기 위해 시나리오별 상태를 보관한다.
 */
object ApiClient {
    private data class MockState(
        var running: Boolean = false,
        var totalSent: Long = 0,
        var totalFailed: Long = 0,
        var configuredRate: Int = 0,
    )

    private val states: MutableMap<Long, MockState> = mutableMapOf()
    private var nextId = 4L

    private val scenarios = mutableListOf(
        ScenarioResponse(1, "Brute Force Test", RequestType.REST, AttackType.BRUTE_FORCE, false, 50, 10),
        ScenarioResponse(2, "SQL Injection Scan", RequestType.REST, AttackType.SQL_INJECTION, false, 30, 5),
        ScenarioResponse(3, "Normal Traffic", RequestType.KAFKA, AttackType.ERROR_SPIKE, true, 100, 20),
    )

    // ── Generator ──────────────────────────────────────────────────────

    suspend fun getGeneratorStatuses(): Result<List<GeneratorStatusResponse>> {
        delay(80)
        // 폴링마다 실행 중인 시나리오의 카운터를 흉내내어 증가시킴
        states.values.forEach { state ->
            if (state.running && state.configuredRate > 0) {
                state.totalSent += Random.nextLong(state.configuredRate.toLong(), state.configuredRate * 2L)
                if (Random.nextFloat() < 0.05f) state.totalFailed += Random.nextLong(1, 5)
            }
        }
        val list = states.entries.map { (id, s) ->
            GeneratorStatusResponse(
                scenarioId = id,
                running = s.running,
                totalSent = s.totalSent,
                totalFailed = s.totalFailed,
                configuredRate = s.configuredRate,
            )
        }
        return Result.success(list)
    }

    suspend fun startGenerator(scenarioId: Long): Result<Unit> {
        delay(200)
        val scenario = scenarios.firstOrNull { it.id == scenarioId }
            ?: return Result.failure(IllegalArgumentException("시나리오를 찾을 수 없습니다: id=$scenarioId"))
        val state = states.getOrPut(scenarioId) { MockState() }
        if (state.running) {
            return Result.failure(IllegalStateException("이미 실행 중입니다: id=$scenarioId"))
        }
        state.running = true
        state.configuredRate = scenario.rate.toInt()
        state.totalSent = 0
        state.totalFailed = 0
        return Result.success(Unit)
    }

    suspend fun stopGenerator(scenarioId: Long): Result<Unit> {
        delay(200)
        val state = states[scenarioId] ?: return Result.success(Unit)
        state.running = false
        state.configuredRate = 0
        return Result.success(Unit)
    }

    suspend fun stopAllGenerators(): Result<Unit> {
        delay(200)
        states.values.forEach {
            it.running = false
            it.configuredRate = 0
        }
        return Result.success(Unit)
    }

    suspend fun burstGenerator(scenarioId: Long): Result<Unit> {
        delay(200)
        val scenario = scenarios.firstOrNull { it.id == scenarioId }
            ?: return Result.failure(IllegalArgumentException("시나리오를 찾을 수 없습니다: id=$scenarioId"))
        val state = states.getOrPut(scenarioId) { MockState() }
        state.totalSent += scenario.rate * 10
        if (Random.nextFloat() < 0.1f) state.totalFailed += Random.nextLong(1, 10)
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
        states.remove(id)
        return Result.success(Unit)
    }
}
