package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.ingest.application.dto.command.IngestBatchCommand

data class IngestBatchRequest(
    val events: List<IngestEventRequest>,
) {
    fun toCommand(): IngestBatchCommand = IngestBatchCommand(
        events = events.map { it.toCommand() },
    )
}
