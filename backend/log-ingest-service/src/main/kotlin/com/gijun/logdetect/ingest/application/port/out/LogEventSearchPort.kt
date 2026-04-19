package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.common.domain.model.LogEvent

interface LogEventSearchPort {

    fun index(event: LogEvent)

    fun indexBatch(events: List<LogEvent>)
}
