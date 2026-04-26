package com.gijun.logdetect.ingest.infrastructure.adapter.out.cache

import com.gijun.logdetect.ingest.application.port.out.RateLimitCachePort
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Redisson 기반 brute-force 방어 카운터/락 어댑터.
 *
 * WHY — Redis(Redisson) 의 RAtomicLong + expire 로 분산 환경 단일 카운터를 보장.
 * 다중 인스턴스 환경에서도 동일 IP 의 실패 횟수가 일관성 있게 누적된다.
 *
 *  - failure:{key}   → RAtomicLong (실패 카운터, ttl 자동 만료)
 *  - lockout:{key}   → RBucket<Long> (락아웃 만료 epochSecond)
 */
@Component
class RateLimitCacheAdapter(
    private val redissonClient: RedissonClient,
) : RateLimitCachePort {

    override fun incrementFailure(key: String, ttl: Duration): Long {
        val counter = redissonClient.getAtomicLong(failureKey(key))
        val current = counter.incrementAndGet()
        // 첫 증가(=1)일 때만 ttl 을 설정. 이후 갱신되지 않아 슬라이딩이 아닌 fixed window 동작.
        // 운영 의도 — window 안에 5회 누적되면 lock, 그렇지 못하면 자연 만료(window 리셋).
        if (current == 1L) {
            counter.expire(ttl)
        }
        return current
    }

    override fun resetFailure(key: String) {
        redissonClient.getAtomicLong(failureKey(key)).delete()
    }

    override fun lock(key: String, ttl: Duration) {
        // 락아웃은 별도 bucket 으로 두어 카운터와 ttl 정책을 분리.
        // 값은 단순 마커(1), ttl 만료 시 자동 해제.
        val bucket = redissonClient.getBucket<Long>(lockoutKey(key))
        bucket.set(1L, ttl.toMillis(), TimeUnit.MILLISECONDS)
    }

    override fun isLocked(key: String): Boolean =
        redissonClient.getBucket<Long>(lockoutKey(key)).isExists

    override fun lockTtlSeconds(key: String): Long {
        val remaining = redissonClient.getBucket<Long>(lockoutKey(key)).remainTimeToLive()
        // -2: not exists, -1: no expire — 둘 다 lock 아닌 상태로 보고 0 반환.
        return if (remaining <= 0) 0L else remaining / 1000L
    }

    private fun failureKey(key: String) = "$KEY_FAILURE_PREFIX$key"
    private fun lockoutKey(key: String) = "$KEY_LOCKOUT_PREFIX$key"

    companion object {
        private const val KEY_FAILURE_PREFIX = "ingest:auth:failure:"
        private const val KEY_LOCKOUT_PREFIX = "ingest:auth:lockout:"
    }
}
