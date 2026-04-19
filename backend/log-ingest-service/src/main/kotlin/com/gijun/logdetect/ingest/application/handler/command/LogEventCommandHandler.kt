package com.gijun.logdetect.ingest.application.handler.command

import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.dto.command.IngestBatchCommand
import com.gijun.logdetect.ingest.application.dto.command.IngestEventCommand
import com.gijun.logdetect.ingest.application.dto.result.LogEventResult
import com.gijun.logdetect.ingest.application.port.`in`.command.IngestEventUseCase
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

open class LogEventCommandHandler(
    private val logEventPersistencePort: LogEventPersistencePort,
    private val logEventMessagePort: LogEventMessagePort,
    private val logEventSearchPort: LogEventSearchPort,
) : IngestEventUseCase {

    @Transactional
    override fun ingest(command: IngestEventCommand): LogEventResult {
        val event = toLogEvent(command)
        val (saved, ingestedAt) = logEventPersistencePort.save(event)
        logEventSearchPort.index(saved)
        logEventMessagePort.publishRaw(saved)
        return LogEventResult.from(saved, ingestedAt)
    }

    @Transactional
    override fun ingestBatch(command: IngestBatchCommand): List<LogEventResult> {
        val events = command.events.map { toLogEvent(it) }
        val savedPairs = logEventPersistencePort.saveAll(events)
        val savedEvents = savedPairs.map { it.first }
        logEventSearchPort.indexBatch(savedEvents)
        logEventMessagePort.publishRawBatch(savedEvents)
        return savedPairs.map { (event, ingestedAt) -> LogEventResult.from(event, ingestedAt) }
    }

    private fun toLogEvent(command: IngestEventCommand): LogEvent =
        LogEvent(
            eventId = UUID.randomUUID(),
            source = command.source,
            level = LogLevel.valueOf(command.level.uppercase()),
            message = command.message,
            timestamp = command.timestamp,
            host = command.host,
            ip = command.ip,
            userId = command.userId,
            attributes = command.attributes,
        )
}
