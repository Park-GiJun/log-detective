package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.common.domain.model.LogEvent
import java.time.Instant
import java.util.UUID

interface LogEventPersistencePort {

    fun save(event: LogEvent): Pair<LogEvent, Instant>

    fun saveAll(events: List<LogEvent>): List<Pair<LogEvent, Instant>>

    fun findByEventId(eventId: UUID): Pair<LogEvent, Instant>?

    fun findRecent(limit: Int): List<Pair<LogEvent, Instant>>
}
