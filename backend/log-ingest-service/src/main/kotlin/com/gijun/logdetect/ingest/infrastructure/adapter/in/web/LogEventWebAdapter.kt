package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web

import com.gijun.logdetect.ingest.application.port.`in`.command.IngestEventUseCase
import com.gijun.logdetect.ingest.application.port.`in`.query.GetLogEventUseCase
import com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.dto.IngestBatchRequest
import com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.dto.IngestEventRequest
import com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.dto.LogEventResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/logs")
@Validated
class LogEventWebAdapter(
    private val ingestEventUseCase: IngestEventUseCase,
    private val getLogEventUseCase: GetLogEventUseCase,
) {

    @PostMapping
    fun ingestEvent(@Valid @RequestBody request: IngestEventRequest): ResponseEntity<LogEventResponse> {
        val result = ingestEventUseCase.ingest(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(LogEventResponse.from(result))
    }

    @PostMapping("/batch")
    fun ingestBatch(@Valid @RequestBody request: IngestBatchRequest): ResponseEntity<List<LogEventResponse>> {
        val results = ingestEventUseCase.ingestBatch(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(results.map { LogEventResponse.from(it) })
    }

    @GetMapping("/{eventId}")
    fun getEvent(@PathVariable eventId: UUID): ResponseEntity<LogEventResponse> {
        val result = getLogEventUseCase.getByEventId(eventId)
        return ResponseEntity.ok(LogEventResponse.from(result))
    }

    @GetMapping
    fun getRecentEvents(
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(MAX_LIMIT.toLong())
        limit: Int,
    ): ResponseEntity<List<LogEventResponse>> {
        val results = getLogEventUseCase.getRecent(limit)
        return ResponseEntity.ok(results.map { LogEventResponse.from(it) })
    }

    companion object {
        const val MAX_LIMIT: Int = 1000
    }
}
