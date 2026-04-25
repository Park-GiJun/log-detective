package com.gijun.logdetect.generator.infrastructure.adapter.out.cache

import com.gijun.logdetect.generator.application.port.out.GeneratorStateCachePort
import com.gijun.logdetect.generator.domain.model.GeneratorStatus
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

/**
 * 시나리오별로 키를 분리해 다중 시나리오 동시 실행을 지원한다.
 *
 *  - generator:state:{scenarioId}:running   → Bucket<Boolean>
 *  - generator:state:{scenarioId}:rate      → AtomicLong
 *  - generator:state:{scenarioId}:sent      → AtomicLong
 *  - generator:state:{scenarioId}:failed    → AtomicLong
 *  - generator:state:active                 → Set<Long>  (실행 중인 시나리오 모음)
 */
@Component
class GeneratorStateCacheAdapter(
    private val redissonClient: RedissonClient,
) : GeneratorStateCachePort {

    override fun markRunning(scenarioId: Long, rate: Int) {
        redissonClient.getBucket<Boolean>(runningKey(scenarioId)).set(true)
        redissonClient.getAtomicLong(rateKey(scenarioId)).set(rate.toLong())
        redissonClient.getSet<Long>(KEY_ACTIVE).add(scenarioId)
    }

    override fun markStopped(scenarioId: Long) {
        redissonClient.getBucket<Boolean>(runningKey(scenarioId)).set(false)
        redissonClient.getAtomicLong(rateKey(scenarioId)).set(0)
        redissonClient.getSet<Long>(KEY_ACTIVE).remove(scenarioId)
    }

    override fun incrementSent(scenarioId: Long): Long =
        redissonClient.getAtomicLong(sentKey(scenarioId)).incrementAndGet()

    override fun incrementFailed(scenarioId: Long): Long =
        redissonClient.getAtomicLong(failedKey(scenarioId)).incrementAndGet()

    override fun resetCounters(scenarioId: Long) {
        redissonClient.getAtomicLong(sentKey(scenarioId)).set(0)
        redissonClient.getAtomicLong(failedKey(scenarioId)).set(0)
    }

    override fun getStatus(scenarioId: Long): GeneratorStatus = GeneratorStatus(
        scenarioId = scenarioId,
        running = redissonClient.getBucket<Boolean>(runningKey(scenarioId)).get() ?: false,
        totalSent = redissonClient.getAtomicLong(sentKey(scenarioId)).get(),
        totalFailed = redissonClient.getAtomicLong(failedKey(scenarioId)).get(),
        configuredRate = redissonClient.getAtomicLong(rateKey(scenarioId)).get().toInt(),
    )

    override fun getActiveScenarioIds(): Set<Long> =
        redissonClient.getSet<Long>(KEY_ACTIVE).readAll()

    override fun getAllStatuses(): List<GeneratorStatus> =
        getActiveScenarioIds().map { getStatus(it) }

    private fun runningKey(id: Long) = "$KEY_PREFIX$id:running"
    private fun rateKey(id: Long) = "$KEY_PREFIX$id:rate"
    private fun sentKey(id: Long) = "$KEY_PREFIX$id:sent"
    private fun failedKey(id: Long) = "$KEY_PREFIX$id:failed"

    companion object {
        private const val KEY_PREFIX = "generator:state:"
        private const val KEY_ACTIVE = "${KEY_PREFIX}active"
    }
}
