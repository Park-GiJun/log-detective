package com.gijun.logdetect.ingest.application.handler.command

import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.common.topic.KafkaTopics
import com.gijun.logdetect.ingest.application.dto.command.IngestBatchCommand
import com.gijun.logdetect.ingest.application.dto.command.IngestEventCommand
import com.gijun.logdetect.ingest.application.dto.result.LogEventResult
import com.gijun.logdetect.ingest.application.port.`in`.command.IngestEventUseCase
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.application.port.out.OutboxPayloadSerializerPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.application.port.out.SearchIndexResolverPort
import com.gijun.logdetect.ingest.domain.Clock
import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.model.Outbox
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 로그 인입 + Outbox 행 생성 핸들러.
 *
 * WHY — Outbox 패턴: log_events insert 와 outbox insert 를 한 트랜잭션으로 묶어
 * "이벤트 저장 + 발행 의도 등록" 의 원자성을 확보한다. 외부 IO (ES / Kafka) 는
 * [com.gijun.logdetect.ingest.application.handler.command.DispatchOutboxHandler] 가 별도 사이클에서 처리한다.
 *
 * 인프라 의존 분리 (이슈 #45):
 * - 직렬화: [OutboxPayloadSerializerPort] 가 책임 (Jackson 어댑터에 격리).
 * - ES 인덱스명: [SearchIndexResolverPort] 가 결정 (ES 운영 정책이 application 으로 새지 않음).
 * - 시간: [Clock] 도메인 포트로 주입 (이슈 #52).
 *
 * write amplification (이슈 #46):
 * 한 LogEvent 당 outbox 2 행이지만 payload 직렬화는 1회만 수행하여 동일 String 을 공유한다.
 * 더 근본적인 분리 (log_events.payload + outbox.fk) 는 별도 PR 로 분리.
 */
open class LogEventCommandHandler(
    private val logEventPersistencePort: LogEventPersistencePort,
    private val outboxPersistencePort: OutboxPersistencePort,
    private val payloadSerializerPort: OutboxPayloadSerializerPort,
    private val searchIndexResolverPort: SearchIndexResolverPort,
    private val clock: Clock,
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
        // 한 이벤트당 직렬화 1회 — 두 outbox 행이 동일 payload String 을 공유 (#46 완화).
        val payload = payloadSerializerPort.serialize(event)
        val esIndex = searchIndexResolverPort.resolveIndexName(event.timestamp)
        val aggregateId = event.eventId.toString()
        return listOf(
            Outbox.newPending(clock, aggregateId, ChannelType.ES, esIndex, payload),
            Outbox.newPending(clock, aggregateId, ChannelType.KAFKA, KafkaTopics.LOGS_RAW, payload),
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
}
