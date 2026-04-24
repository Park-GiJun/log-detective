package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.common.domain.model.LogEvent
import java.time.Instant
import java.util.UUID

interface LogEventPersistencePort {

    suspend fun save(event: LogEvent): Pair<LogEvent, Instant>

    suspend fun saveAll(events: List<LogEvent>): List<Pair<LogEvent, Instant>>

    suspend fun findByEventId(eventId: UUID): Pair<LogEvent, Instant>?

    suspend fun findRecent(limit: Int): List<Pair<LogEvent, Instant>>
}
