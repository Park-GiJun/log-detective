package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.adapter

import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.model.Outbox
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.entity.OutboxEntity
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.repository.OutboxJpaRepository
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class OutboxPersistenceAdapter(
    private val repository: OutboxJpaRepository,
) : OutboxPersistencePort {

    override fun saveAll(outboxes: List<Outbox>) {
        repository.saveAll(outboxes.map { OutboxEntity.from(it) })
    }

    override fun fetchPending(limit: Int): List<Outbox> {
        // WHY: 외부 입력으로 limit 이 확장될 가능성에 대비한 방어. 비정상 큰 값으로
        // SELECT ... FOR UPDATE 를 일으키면 락 점유 시간이 폭증해 DoS 로 이어진다.
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        return repository.fetchPendingForUpdate(safeLimit).map { it.toDomain() }
    }

    override fun markPublished(id: Long) {
        repository.markPublished(id, Instant.now())
    }

    override fun markPublishedAll(ids: List<Long>) {
        if (ids.isEmpty()) return
        repository.markPublishedAll(ids, Instant.now())
    }

    override fun markFailed(id: Long, error: String, nextAttemptAt: Instant) {
        repository.markFailed(id, error, nextAttemptAt)
    }

    override fun markDead(id: Long, error: String) {
        repository.markDead(id, error)
    }

    companion object {
        private const val MAX_LIMIT = 1000
    }
}
