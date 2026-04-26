package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.logEvent.repository

import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.logEvent.entity.LogEventEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LogEventJpaRepository : JpaRepository<LogEventEntity, Long> {

    fun findByEventId(eventId: UUID): LogEventEntity?

    fun findAllByOrderByEventTimestampDesc(pageable: Pageable): List<LogEventEntity>
}
