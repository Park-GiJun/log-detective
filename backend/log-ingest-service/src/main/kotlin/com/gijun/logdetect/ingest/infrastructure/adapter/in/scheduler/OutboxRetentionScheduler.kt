package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.scheduler

import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.Clock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
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
 * 트랜잭션 — port 의 purge* 메서드 각각이 REQUIRES_NEW 자체 트랜잭션을 보유하므로
 * 스케줄러 레이어에는 별도 @Transactional 을 두지 않는다. 두 purge 가 독립 트랜잭션이라
 * 한 쪽이 실패해도 다른 쪽은 영향이 없다 (의도된 동작).
 *
 * 주기 — 매일 03:00 (KST). 새벽대 부하가 가장 낮은 시간을 선택. cron 은
 * `logdetect.outbox.retention.cron` 으로 오버라이드 가능.
 */
@Component
class OutboxRetentionScheduler(
    private val outboxPersistencePort: OutboxPersistencePort,
    private val clock: Clock,
    @Value("\${logdetect.outbox.retention.published-days:7}")
    private val publishedDays: Long,
    @Value("\${logdetect.outbox.retention.dead-days:30}")
    private val deadDays: Long,
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Scheduled(cron = "\${logdetect.outbox.retention.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    fun purge() {
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
}
