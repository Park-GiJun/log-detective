package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.ingest.domain.model.Outbox
import java.time.Instant

interface OutboxPersistencePort {
    fun saveAll(outboxes: List<Outbox>)
    fun fetchPending(limit: Int): List<Outbox>
    fun markPublished(id: Long)
    fun markFailed(id: Long, error: String, nextAttemptAt: Instant)
    fun markDead(id: Long, error: String)
}
