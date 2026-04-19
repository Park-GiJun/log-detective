package com.gijun.logdetect.ingest.application.port.`in`.query

import com.gijun.logdetect.ingest.application.dto.result.LogEventResult
import java.util.UUID

interface GetLogEventUseCase {

    fun getByEventId(eventId: UUID): LogEventResult

    fun getRecent(limit: Int): List<LogEventResult>
}
