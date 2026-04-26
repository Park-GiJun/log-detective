package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.domain.model.IngestedLogEvent
import java.util.UUID

interface LogEventPersistencePort {

    fun save(event: LogEvent): IngestedLogEvent

    fun saveAll(events: List<LogEvent>): List<IngestedLogEvent>

    fun findByEventId(eventId: UUID): IngestedLogEvent?

    fun findRecent(limit: Int): List<IngestedLogEvent>
}
