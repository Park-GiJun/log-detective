package com.gijun.logdetect.ingest.infrastructure.config

import com.gijun.logdetect.ingest.domain.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock as JavaClock

/**
 * 도메인 [Clock] 의 운영 구현 — UTC 기준 시스템 시계.
 *
 * WHY — 시계 정책을 단일 빈으로 모아두면, 테스트는 별도 [Clock] 빈을 등록해 시간을 고정하거나
 * 옮길 수 있다. 본 서비스는 outbox / payload timestamp 모두 UTC 로 통일하므로 systemUTC 사용.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock {
        val systemUtc = JavaClock.systemUTC()
        return Clock { systemUtc.instant() }
    }
}
