package com.gijun.logdetect.ingest.application.port.`in`.query

import com.gijun.logdetect.ingest.application.dto.result.LogEventResult
import java.util.UUID

interface GetLogEventUseCase {

    suspend fun getByEventId(eventId: UUID): LogEventResult

    suspend fun getRecent(limit: Int): List<LogEventResult>
}
