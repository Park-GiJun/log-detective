package com.gijun.logdetect.ingest.application.handler.query

import com.gijun.fds.common.exception.DomainNotFoundException
import com.gijun.logdetect.ingest.application.dto.result.LogEventResult
import com.gijun.logdetect.ingest.application.port.`in`.query.GetLogEventUseCase
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import java.util.UUID

class LogEventQueryHandler(
    private val logEventPersistencePort: LogEventPersistencePort,
) : GetLogEventUseCase {

    override fun getByEventId(eventId: UUID): LogEventResult {
        val (event, ingestedAt) = logEventPersistencePort.findByEventId(eventId)
            ?: throw DomainNotFoundException("LogEvent를 찾을 수 없습니다: $eventId")
        return LogEventResult.from(event, ingestedAt)
    }

    override fun getRecent(limit: Int): List<LogEventResult> =
        logEventPersistencePort.findRecent(limit)
            .map { (event, ingestedAt) -> LogEventResult.from(event, ingestedAt) }
}
