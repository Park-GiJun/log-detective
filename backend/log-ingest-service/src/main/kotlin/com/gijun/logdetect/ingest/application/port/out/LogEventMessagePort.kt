package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.common.domain.model.LogEvent

interface LogEventMessagePort {

    suspend fun publishRaw(event: LogEvent)

    suspend fun publishRawBatch(events: List<LogEvent>)
}
