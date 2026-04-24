package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.common.domain.model.LogEvent

interface LogEventSearchPort {

    suspend fun index(event: LogEvent)

    suspend fun indexBatch(events: List<LogEvent>)
}
