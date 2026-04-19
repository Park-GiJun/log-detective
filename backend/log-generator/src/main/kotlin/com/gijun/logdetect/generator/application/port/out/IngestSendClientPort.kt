package com.gijun.logdetect.generator.application.port.out

import com.gijun.logdetect.generator.domain.model.LogEvent

interface IngestSendClientPort {
    suspend fun send(log: LogEvent) : Boolean
}
