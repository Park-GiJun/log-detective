package com.gijun.logdetect.ingest.application.handler.command

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.common.topic.KafkaTopics
import com.gijun.logdetect.ingest.application.dto.command.IngestBatchCommand
import com.gijun.logdetect.ingest.application.dto.command.IngestEventCommand
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.application.port.out.OutboxPayloadSerializerPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.application.port.out.SearchIndexResolverPort
import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.enums.OutboxStatus
import com.gijun.logdetect.ingest.domain.model.IngestedLogEvent
import com.gijun.logdetect.ingest.domain.model.Outbox
import com.gijun.logdetect.ingest.domain.port.Clock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class LogEventCommandHandlerTest : DescribeSpec({

    val esDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    val fixedNow: Instant = Instant.parse("2026-04-26T10:00:00Z")

    fun objectMapperForTest(): ObjectMapper =
        jacksonObjectMapper().registerModule(JavaTimeModule())

    fun jacksonSerializer(mapper: ObjectMapper) =
        OutboxPayloadSerializerPort { event -> mapper.writeValueAsString(event) }

    fun esIndexResolver() = SearchIndexResolverPort { ts ->
        "logs-${ts.atOffset(ZoneOffset.UTC).format(esDateFormatter)}"
    }

    fun fixedClock(now: Instant = fixedNow) = Clock { now }

    fun newHandler(
        persistencePort: LogEventPersistencePort = mockk(),
        outboxPort: OutboxPersistencePort = mockk(relaxed = true),
        mapper: ObjectMapper = objectMapperForTest(),
    ) = LogEventCommandHandler(
        logEventPersistencePort = persistencePort,
        outboxPersistencePort = outboxPort,
        payloadSerializerPort = jacksonSerializer(mapper),
        searchIndexResolverPort = esIndexResolver(),
        clock = fixedClock(),
    )

    fun ingestCommand(
        timestamp: Instant = fixedNow,
    ) = IngestEventCommand(
        source = "test-source",
        level = "INFO",
        message = "hello",
        timestamp = timestamp,
        host = "host-1",
        ip = "10.0.0.1",
        userId = "user-1",
        attributes = mapOf("k" to "v"),
    )

    describe("ingest") {
        it("LogEvent 한 건당 ES + KAFKA 두 개의 outbox 행을 PENDING 상태로 생성한다") {
            val persistencePort = mockk<LogEventPersistencePort>()
            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val handler = newHandler(persistencePort, outboxPort)

            val savedEvent = LogEvent(
                eventId = UUID.randomUUID(),
                source = "test-source",
                level = LogLevel.INFO,
                message = "hello",
                timestamp = fixedNow,
                host = "host-1",
                ip = "10.0.0.1",
                userId = "user-1",
                attributes = mapOf("k" to "v"),
            )
            every { persistencePort.save(any()) } returns IngestedLogEvent(savedEvent, fixedNow)

            val captured = slot<List<Outbox>>()
            every { outboxPort.saveAll(capture(captured)) } returns Unit

            handler.ingest(ingestCommand())

            captured.captured shouldHaveSize 2
            captured.captured.map { it.channel } shouldBe listOf(ChannelType.ES, ChannelType.KAFKA)
            captured.captured.forEach {
                it.status shouldBe OutboxStatus.PENDING
                it.attempts shouldBe 0
                it.aggregateId shouldBe savedEvent.eventId.toString()
            }
        }

        it("ES outbox 의 destination 은 logs-yyyy.MM.dd UTC 패턴이고, KAFKA outbox 의 destination 은 logs.raw 토픽이다") {
            val persistencePort = mockk<LogEventPersistencePort>()
            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val handler = newHandler(persistencePort, outboxPort)

            val ts = Instant.parse("2026-04-26T15:30:00Z")
            val saved = LogEvent(
                eventId = UUID.randomUUID(),
                source = "s",
                level = LogLevel.INFO,
                message = "m",
                timestamp = ts,
            )
            every { persistencePort.save(any()) } returns IngestedLogEvent(saved, fixedNow)

            val captured = slot<List<Outbox>>()
            every { outboxPort.saveAll(capture(captured)) } returns Unit

            handler.ingest(ingestCommand(ts))

            val expectedEsIndex = "logs-${ts.atOffset(ZoneOffset.UTC).format(esDateFormatter)}"
            captured.captured.first { it.channel == ChannelType.ES }.destination shouldBe expectedEsIndex
            captured.captured.first { it.channel == ChannelType.KAFKA }.destination shouldBe KafkaTopics.LOGS_RAW
        }

        it("payload 는 LogEvent 를 JSON 직렬화한 결과이며 두 outbox 행이 동일한 payload 를 공유한다") {
            val persistencePort = mockk<LogEventPersistencePort>()
            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val mapper = objectMapperForTest()
            val handler = newHandler(persistencePort, outboxPort, mapper)

            val saved = LogEvent(
                eventId = UUID.randomUUID(),
                source = "s",
                level = LogLevel.WARN,
                message = "m",
                timestamp = fixedNow,
            )
            every { persistencePort.save(any()) } returns IngestedLogEvent(saved, fixedNow)

            val captured = slot<List<Outbox>>()
            every { outboxPort.saveAll(capture(captured)) } returns Unit

            handler.ingest(ingestCommand())

            val expected = mapper.writeValueAsString(saved)
            captured.captured.forEach { it.payload shouldBe expected }
            captured.captured[0].payload shouldBe captured.captured[1].payload
        }
    }

    describe("ingestBatch") {
        it("이벤트 N건이면 outbox 2N건이 한 번에 saveAll 로 저장된다") {
            val persistencePort = mockk<LogEventPersistencePort>()
            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val handler = newHandler(persistencePort, outboxPort)

            val n = 3
            val savedEvents = (1..n).map {
                LogEvent(
                    eventId = UUID.randomUUID(),
                    source = "s$it",
                    level = LogLevel.INFO,
                    message = "m$it",
                    timestamp = fixedNow,
                )
            }
            every { persistencePort.saveAll(any()) } returns savedEvents.map { IngestedLogEvent(it, fixedNow) }

            val captured = slot<List<Outbox>>()
            every { outboxPort.saveAll(capture(captured)) } returns Unit

            handler.ingestBatch(IngestBatchCommand(events = List(n) { ingestCommand() }))

            captured.captured shouldHaveSize n * 2
            captured.captured.count { it.channel == ChannelType.ES } shouldBe n
            captured.captured.count { it.channel == ChannelType.KAFKA } shouldBe n
            verify(exactly = 1) { outboxPort.saveAll(any()) }
        }

        it("batch 가 빈 리스트면 outbox saveAll 은 빈 리스트로 호출된다") {
            val persistencePort = mockk<LogEventPersistencePort>()
            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val handler = newHandler(persistencePort, outboxPort)

            every { persistencePort.saveAll(emptyList()) } returns emptyList()

            val captured = slot<List<Outbox>>()
            every { outboxPort.saveAll(capture(captured)) } returns Unit

            handler.ingestBatch(IngestBatchCommand(events = emptyList()))

            captured.captured shouldHaveSize 0
        }
    }
})
