package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.scheduler

import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.Clock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration

/**
 * Outbox retention 잡 — 보관 기한 초과 행을 주기적으로 purge 한다 (이슈 #49).
 *
 * WHY — `outbox_messages.payload (jsonb)` 가 LogEvent 전체 (ip, userId, message,
 * attributes) 를 평문으로 보관하므로, retention 정책 없이 누적되면 GDPR /
 * 개인정보보호법 보관 기한을 초과한다. PUBLISHED 는 dispatch 완료된 사본이라
 * 짧은 기한 (default 7d), DEAD 는 운영자 분석 여유를 두고 30d 후 purge.
 *
 * 본 PR 은 단순 purge — DEAD archive(S3) 는 별도 이슈로 분리한다.
 *
 * 위치 (이슈 #43 컨벤션) — 외부 트리거인 스케줄러는 `infrastructure/adapter/in/scheduler` 에 둔다.
 *
 * 분산 락 (이슈 #95) — multi-instance 운영 시 N개 인스턴스가 동시에 `@Scheduled` 를
 * 트리거하면 동일 행에 대한 DELETE 경합 + 중복 작업이 발생한다. PostgreSQL 의
 * `pg_try_advisory_xact_lock` 으로 트랜잭션 범위 advisory lock 을 잡아, 락을 획득한
 * 단일 인스턴스만 purge 를 수행한다. 트랜잭션 종료 시 락이 자동 해제되므로 별도
 * 해제 로직이 불필요하고, 인스턴스 충돌 / 비정상 종료 시에도 데드락이 남지 않는다.
 *
 * 트랜잭션 — 전체 메서드를 `@Transactional` 로 감싸 advisory lock 의 lifecycle 을
 * 트랜잭션과 일치시킨다. port 의 purge* 가 내부적으로 REQUIRES_NEW 를 쓰더라도
 * advisory lock 자체는 외부 트랜잭션이 보유한다. 두 purge 가 분리된 자체 트랜잭션을
 * 가지므로 한쪽 실패가 다른 쪽에 영향을 주지 않는 동작은 그대로 유지된다.
 *
 * 주기 — 매일 03:00 (KST). 새벽대 부하가 가장 낮은 시간을 선택. cron 은
 * `logdetect.outbox.retention.cron` 으로 오버라이드 가능.
 */
@Component
class OutboxRetentionScheduler(
    private val outboxPersistencePort: OutboxPersistencePort,
    private val clock: Clock,
    private val jdbcTemplate: NamedParameterJdbcTemplate,
    @Value("\${logdetect.outbox.retention.published-days:7}")
    private val publishedDays: Long,
    @Value("\${logdetect.outbox.retention.dead-days:30}")
    private val deadDays: Long,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Scheduled(cron = "\${logdetect.outbox.retention.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    @Transactional
    fun purge() {
        // WHY — 트랜잭션 advisory lock 을 먼저 시도. 다른 인스턴스가 먼저 잡았다면 false.
        val params = MapSqlParameterSource("lockId", RETENTION_LOCK_ID)
        val acquired = jdbcTemplate.queryForObject(
            "select pg_try_advisory_xact_lock(:lockId)",
            params,
            Boolean::class.java,
        ) ?: false

        if (!acquired) {
            logger.info("Outbox retention purge skipped — another instance holds the advisory lock")
            return
        }

        val now = clock.now()
        val publishedThreshold = now.minus(Duration.ofDays(publishedDays))
        val deadThreshold = now.minus(Duration.ofDays(deadDays))

        val publishedDeleted = outboxPersistencePort.purgePublishedOlderThan(publishedThreshold)
        val deadDeleted = outboxPersistencePort.purgeDeadOlderThan(deadThreshold)

        logger.info(
            "Outbox retention purge — PUBLISHED: {} (>{}d), DEAD: {} (>{}d)",
            publishedDeleted,
            publishedDays,
            deadDeleted,
            deadDays,
        )
    }

    companion object {
        /**
         * Advisory lock 식별자.
         *
         * WHY — bigint 상수로 충돌 회피. 상위 4바이트에 ASCII "Outb" (0x4F757462) 를 두어
         * 의미 있는 매직 넘버로 만든다. 하위 4바이트는 시퀀스 (현재 1) — 향후 다른 advisory
         * lock 이 추가될 경우 같은 prefix + 새로운 시퀀스로 네임스페이스를 분리한다.
         * PostgreSQL advisory lock 은 단일 bigint 또는 (int,int) 두 가지 키 형식을 지원하며
         * 본 코드는 bigint 단일 키를 사용한다.
         */
        const val RETENTION_LOCK_ID: Long = 0x4F75_7462_0000_0001L
    }
}
