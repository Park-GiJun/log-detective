package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.logEvent.adapter

import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.logEvent.entity.LogEventEntity
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.logEvent.repository.LogEventJpaRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class LogEventPersistenceAdapter(
    private val logEventJpaRepository: LogEventJpaRepository,
) : LogEventPersistencePort {

    override fun save(event: LogEvent): Pair<LogEvent, Instant> {
        val saved = logEventJpaRepository.save(LogEventEntity.from(event))
        return saved.toDomain() to (saved.ingestedAt ?: Instant.now())
    }

    override fun saveAll(events: List<LogEvent>): List<Pair<LogEvent, Instant>> =
        logEventJpaRepository.saveAll(events.map { LogEventEntity.from(it) })
            .map { it.toDomain() to (it.ingestedAt ?: Instant.now()) }

    override fun findByEventId(eventId: UUID): Pair<LogEvent, Instant>? =
        logEventJpaRepository.findByEventId(eventId)
            ?.let { it.toDomain() to (it.ingestedAt ?: Instant.now()) }

    override fun findRecent(limit: Int): List<Pair<LogEvent, Instant>> =
        logEventJpaRepository.findTop100ByOrderByEventTimestampDesc()
            .take(limit)
            .map { it.toDomain() to (it.ingestedAt ?: Instant.now()) }
}
