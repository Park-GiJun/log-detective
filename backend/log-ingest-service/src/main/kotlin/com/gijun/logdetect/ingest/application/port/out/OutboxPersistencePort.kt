package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.ingest.domain.model.Outbox
import java.time.Instant

interface OutboxPersistencePort {
    fun saveAll(outbox: List<Outbox>)
    fun fetchPublished(transactionId: String) : List<Outbox>
    fun markPublished(transactionId: String)
    fun markFailed(transactionId: String, error: String, nextAttemptAt: Instant)
    fun markDead(transactionId: String, error: String)
}
