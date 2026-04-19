package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.common.domain.model.LogEvent

interface LogEventMessagePort {

    fun publishRaw(event: LogEvent)

    fun publishRawBatch(events: List<LogEvent>)
}
