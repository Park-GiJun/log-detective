package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.ingest.application.dto.command.IngestBatchCommand
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

/**
 * 배치 입력 — events 는 비어있지 않아야 하고 한 번에 최대 MAX_BATCH_SIZE 건.
 * @field:Valid 로 각 IngestEventRequest 의 검증도 함께 트리거된다.
 */
data class IngestBatchRequest(
    @field:NotEmpty
    @field:Size(max = MAX_BATCH_SIZE)
    @field:Valid
    val events: List<IngestEventRequest>,
) {
    fun toCommand(): IngestBatchCommand = IngestBatchCommand(
        events = events.map { it.toCommand() },
    )

    companion object {
        const val MAX_BATCH_SIZE = 1000
    }
}
