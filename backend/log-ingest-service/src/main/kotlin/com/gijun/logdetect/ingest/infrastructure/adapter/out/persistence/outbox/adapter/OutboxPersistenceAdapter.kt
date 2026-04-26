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

    override fun fetchPending(limit: Int): List<Outbox> =
        repository.fetchPendingForUpdate(limit).map { it.toDomain() }

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
}
