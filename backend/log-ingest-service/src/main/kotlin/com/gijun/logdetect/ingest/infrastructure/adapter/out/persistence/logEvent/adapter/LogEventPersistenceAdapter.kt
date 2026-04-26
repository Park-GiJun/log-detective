package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.logEvent.adapter

import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.domain.model.IngestedLogEvent
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.logEvent.entity.LogEventEntity
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.logEvent.repository.LogEventJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class LogEventPersistenceAdapter(
    private val logEventJpaRepository: LogEventJpaRepository,
) : LogEventPersistencePort {

    override fun save(event: LogEvent): IngestedLogEvent =
        logEventJpaRepository.save(LogEventEntity.from(event)).toIngested()

    override fun saveAll(events: List<LogEvent>): List<IngestedLogEvent> =
        logEventJpaRepository.saveAll(events.map { LogEventEntity.from(it) })
            .map { it.toIngested() }

    override fun findByEventId(eventId: UUID): IngestedLogEvent? =
        logEventJpaRepository.findByEventId(eventId)?.toIngested()

    override fun findRecent(limit: Int): List<IngestedLogEvent> =
        logEventJpaRepository.findAllByOrderByEventTimestampDesc(
            PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "eventTimestamp")),
        ).map { it.toIngested() }

    private fun LogEventEntity.toIngested(): IngestedLogEvent =
        IngestedLogEvent(
            event = this.toDomain(),
            ingestedAt = this.ingestedAt ?: Instant.now(),
        )
}
