package com.gijun.logdetect.ingest.application.port.`in`.command

import com.gijun.logdetect.ingest.application.dto.command.IngestBatchCommand
import com.gijun.logdetect.ingest.application.dto.command.IngestEventCommand
import com.gijun.logdetect.ingest.application.dto.result.LogEventResult

interface IngestEventUseCase {

    suspend fun ingest(command: IngestEventCommand): LogEventResult

    suspend fun ingestBatch(command: IngestBatchCommand): List<LogEventResult>
}
