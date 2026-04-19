package com.gijun.logdetect.ingest.application.dto.command

data class IngestBatchCommand(
    val events: List<IngestEventCommand>,
)
