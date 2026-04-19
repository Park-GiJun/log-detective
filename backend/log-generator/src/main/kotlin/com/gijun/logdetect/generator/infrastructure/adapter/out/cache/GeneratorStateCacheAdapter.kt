package com.gijun.logdetect.generator.infrastructure.adapter.out.cache

import com.gijun.logdetect.generator.application.port.out.GeneratorStateCachePort
import com.gijun.logdetect.generator.domain.model.GeneratorStatus
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component

@Component
class GeneratorStateCacheAdapter(
    private val redissonClient: RedissonClient,
) : GeneratorStateCachePort {

    override fun markRunning(rate: Int) {
        redissonClient.getBucket<Boolean>(KEY_RUNNING).set(true)
        redissonClient.getAtomicLong(KEY_RATE).set(rate.toLong())
    }

    override fun markStopped() {
        redissonClient.getBucket<Boolean>(KEY_RUNNING).set(false)
        redissonClient.getAtomicLong(KEY_RATE).set(0)
    }

    override fun incrementSent(): Long =
        redissonClient.getAtomicLong(KEY_SENT).incrementAndGet()

    override fun incrementFailed(): Long =
        redissonClient.getAtomicLong(KEY_FAILED).incrementAndGet()

    override fun resetCounters() {
        redissonClient.getAtomicLong(KEY_SENT).set(0)
        redissonClient.getAtomicLong(KEY_FAILED).set(0)
    }

    override fun getStatus(): GeneratorStatus = GeneratorStatus(
        running = redissonClient.getBucket<Boolean>(KEY_RUNNING).get() ?: false,
        totalSent = redissonClient.getAtomicLong(KEY_SENT).get(),
        totalFailed = redissonClient.getAtomicLong(KEY_FAILED).get(),
        configuredRate = redissonClient.getAtomicLong(KEY_RATE).get().toInt(),
    )

    companion object {
        private const val KEY_PREFIX = "generator:state:"
        private const val KEY_RUNNING = "${KEY_PREFIX}running"
        private const val KEY_SENT = "${KEY_PREFIX}totalSent"
        private const val KEY_FAILED = "${KEY_PREFIX}totalFailed"
        private const val KEY_RATE = "${KEY_PREFIX}currentRate"
    }
}
