package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.adapter

import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort.DeadUpdate
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort.FailureUpdate
import com.gijun.logdetect.ingest.domain.Clock
import com.gijun.logdetect.ingest.domain.model.Outbox
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.entity.OutboxEntity
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.repository.OutboxJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Outbox 영속성 어댑터.
 *
 * 트랜잭션 정책 (이슈 #25):
 * - [fetchPending] 은 자체 트랜잭션 (REQUIRES_NEW). 호출자의 dispatch 시간이 락 점유에 묶이지 않게.
 * - [markPublishedAll] / [markFailedAll] / [markDeadAll] 도 각자 자체 트랜잭션.
 * - [saveAll] 은 호출자 (LogEventCommandHandler) 트랜잭션에 참여 — 이벤트 저장과 원자성 보장.
 * - [purgePublishedOlderThan] / [purgeDeadOlderThan] 은 retention 잡 전용 자체 트랜잭션 (이슈 #49).
 */
@Component
class OutboxPersistenceAdapter(
    private val repository: OutboxJpaRepository,
    private val clock: Clock,
) : OutboxPersistencePort {

    @Transactional(propagation = Propagation.REQUIRED)
    override fun saveAll(outboxes: List<Outbox>) {
        repository.saveAll(outboxes.map { OutboxEntity.from(it) })
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun fetchPending(limit: Int): List<Outbox> {
        // 외부 입력으로 limit 이 비정상 큰 값이 되어 SELECT FOR UPDATE 가 폭주하면 DoS — 상한 강제.
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        return repository.fetchPendingForUpdate(safeLimit).map { it.toDomain() }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markPublishedAll(ids: List<Long>) {
        if (ids.isEmpty()) return
        repository.markPublishedAll(ids, clock.now())
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markFailedAll(failures: List<FailureUpdate>) {
        // 행마다 nextAttemptAt / error 가 다르므로 row-by-row UPDATE — 다만 같은 트랜잭션으로 묶어 commit 1회.
        failures.forEach { repository.markFailed(it.id, it.error, it.nextAttemptAt) }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun markDeadAll(deads: List<DeadUpdate>) {
        deads.forEach { repository.markDead(it.id, it.error) }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun purgePublishedOlderThan(threshold: Instant): Int =
        repository.deletePublishedOlderThan(threshold)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun purgeDeadOlderThan(threshold: Instant): Int =
        repository.deleteDeadOlderThan(threshold)

    companion object {
        private const val MAX_LIMIT = 1000
    }
}
