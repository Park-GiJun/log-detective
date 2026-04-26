package com.gijun.logdetect.ingest.application.handler.command

import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.common.topic.KafkaTopics
import com.gijun.logdetect.ingest.application.dto.command.IngestBatchCommand
import com.gijun.logdetect.ingest.application.dto.command.IngestEventCommand
import com.gijun.logdetect.ingest.application.dto.result.LogEventResult
import com.gijun.logdetect.ingest.application.port.`in`.command.IngestEventUseCase
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.model.Outbox
import org.springframework.transaction.annotation.Transactional
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

open class LogEventCommandHandler(
    private val logEventPersistencePort: LogEventPersistencePort,
    private val outboxPersistencePort: OutboxPersistencePort,
    private val objectMapper: ObjectMapper,
) : IngestEventUseCase {

    @Transactional
    override fun ingest(command: IngestEventCommand): LogEventResult {
        val event = toLogEvent(command)
        val ingested = logEventPersistencePort.save(event)
        outboxPersistencePort.saveAll(outboxesFor(ingested.event))
        return LogEventResult.from(ingested.event, ingested.ingestedAt)
    }

    @Transactional
    override fun ingestBatch(command: IngestBatchCommand): List<LogEventResult> {
        val events = command.events.map { toLogEvent(it) }
        val ingestedList = logEventPersistencePort.saveAll(events)
        outboxPersistencePort.saveAll(ingestedList.flatMap { outboxesFor(it.event) })
        return ingestedList.map { LogEventResult.from(it.event, it.ingestedAt) }
    }

    private fun outboxesFor(event: LogEvent): List<Outbox> {
        val payload = objectMapper.writeValueAsString(event)
        val esIndex = "$ES_INDEX_PREFIX${event.timestamp.atOffset(ZoneOffset.UTC).format(ES_DATE_FORMAT)}"
        val aggregateId = event.eventId.toString()
        return listOf(
            Outbox.newPending(aggregateId, ChannelType.ES, esIndex, payload),
            Outbox.newPending(aggregateId, ChannelType.KAFKA, KafkaTopics.LOGS_RAW, payload),
        )
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

    companion object {
        private const val ES_INDEX_PREFIX = "logs-"
        private val ES_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    }
}
