package com.gijun.logdetect.ingest.application.port.out

import java.time.Duration

/**
 * 인증 실패 brute-force 방어용 카운터/락아웃 포트.
 *
 * WHY — log-ingest-service 인증 필터 (#110) 가 IP 단위로 실패 횟수를 누적해
 * threshold 초과 시 일정 시간 락아웃한다. 분산 환경에서도 단일 카운터를 보장하기 위해
 * Redisson 기반 구현으로 위임 (헥사고날 — application 은 인터페이스만 의존).
 */
interface RateLimitCachePort {

    /**
     * 인증 실패 카운터를 1 증가시키고, ttl 이 지나면 자동 만료시킨다.
     * 첫 증가 시에만 ttl 을 설정하기 위해 RAtomicLong + expireIfNotSet 시맨틱.
     *
     * @return 증가 후 누적 실패 횟수
     */
    fun incrementFailure(key: String, ttl: Duration): Long

    /**
     * 인증 성공 시 카운터를 즉시 0 으로 리셋한다.
     */
    fun resetFailure(key: String)

    /**
     * 락아웃 상태 진입 — 지정 ttl 동안 모든 요청을 차단한다.
     */
    fun lock(key: String, ttl: Duration)

    /**
     * 락아웃 상태인지 확인.
     */
    fun isLocked(key: String): Boolean

    /**
     * 락아웃 잔여 시간 (초). 락아웃이 아니면 0 또는 음수.
     */
    fun lockTtlSeconds(key: String): Long
}
