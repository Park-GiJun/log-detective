package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.repository

import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.entity.LogEventEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LogEventJpaRepository : JpaRepository<LogEventEntity, Long> {

    fun findByEventId(eventId: UUID): LogEventEntity?

    fun findTop100ByOrderByEventTimestampDesc(): List<LogEventEntity>
}
