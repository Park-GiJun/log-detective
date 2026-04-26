package com.gijun.logdetect.ingest.infrastructure.scheduler

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.enums.OutboxStatus
import com.gijun.logdetect.ingest.domain.model.Outbox
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeBetween
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID

class OutboxPublisherTest : DescribeSpec({

    fun objectMapperForTest(): ObjectMapper =
        jacksonObjectMapper().registerModule(JavaTimeModule())

    fun sampleEvent() = LogEvent(
        eventId = UUID.randomUUID(),
        source = "s",
        level = LogLevel.INFO,
        message = "m",
        timestamp = Instant.parse("2026-04-26T10:00:00Z"),
    )

    fun outbox(
        id: Long = 1L,
        channel: ChannelType = ChannelType.ES,
        destination: String = "logs-2026.04.26",
        payload: String,
        attempts: Int = 0,
        status: OutboxStatus = OutboxStatus.PENDING,
    ) = Outbox(
        id = id,
        aggregateId = UUID.randomUUID().toString(),
        channel = channel,
        destination = destination,
        payload = payload,
        status = status,
        attempts = attempts,
        nextAttemptAt = Instant.now(),
        createdAt = Instant.now(),
    )

    describe("pollAndDispatch — empty fetch") {
        it("fetchPending 결과가 비어있으면 어떤 port 도 호출하지 않는다") {
            val outboxPort = mockk<OutboxPersistencePort>()
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>()
            every { outboxPort.fetchPending(any()) } returns emptyList()

            val publisher = OutboxPublisher(outboxPort, searchPort, messagePort, objectMapperForTest())
            publisher.pollAndDispatch()

            verify(exactly = 0) { searchPort.index(any()) }
            verify(exactly = 0) { messagePort.publishRaw(any()) }
            verify(exactly = 0) { outboxPort.markPublishedAll(any()) }
        }
    }

    describe("dispatch — ES 채널") {
        it("ES index 성공 시 markPublishedAll 로 일괄 호출한다") {
            val mapper = objectMapperForTest()
            val event = sampleEvent()
            val payload = mapper.writeValueAsString(event)
            val row = outbox(id = 42L, channel = ChannelType.ES, payload = payload)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)

            val publisher = OutboxPublisher(outboxPort, searchPort, messagePort, mapper)
            publisher.pollAndDispatch()

            verify(exactly = 1) { searchPort.index(any()) }
            verify(exactly = 0) { messagePort.publishRaw(any()) }
            verify(exactly = 1) { outboxPort.markPublishedAll(listOf(42L)) }
        }
    }

    describe("dispatch — KAFKA 채널") {
        it("Kafka publishRaw 성공 시 markPublishedAll 로 일괄 호출한다") {
            val mapper = objectMapperForTest()
            val event = sampleEvent()
            val payload = mapper.writeValueAsString(event)
            val row = outbox(id = 7L, channel = ChannelType.KAFKA, destination = "logs.raw", payload = payload)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)

            val publisher = OutboxPublisher(outboxPort, searchPort, messagePort, mapper)
            publisher.pollAndDispatch()

            verify(exactly = 0) { searchPort.index(any()) }
            verify(exactly = 1) { messagePort.publishRaw(any()) }
            verify(exactly = 1) { outboxPort.markPublishedAll(listOf(7L)) }
        }
    }

    describe("dispatch — 미지원 채널 (FILE / OTHERS)") {
        it("FILE 채널은 dispatch 즉시 markDead 처리된다") {
            val mapper = objectMapperForTest()
            val payload = mapper.writeValueAsString(sampleEvent())
            val row = outbox(id = 99L, channel = ChannelType.FILE, payload = payload)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)

            val publisher = OutboxPublisher(outboxPort, searchPort, messagePort, mapper)
            publisher.pollAndDispatch()

            verify(exactly = 1) { outboxPort.markDead(99L, match { it.contains("unsupported channel") }) }
            verify(exactly = 0) { outboxPort.markPublishedAll(any()) }
            verify(exactly = 0) { searchPort.index(any()) }
            verify(exactly = 0) { messagePort.publishRaw(any()) }
        }

        it("OTHERS 채널도 markDead 로 처리된다") {
            val mapper = objectMapperForTest()
            val payload = mapper.writeValueAsString(sampleEvent())
            val row = outbox(id = 100L, channel = ChannelType.OTHERS, payload = payload)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)

            val publisher = OutboxPublisher(outboxPort, searchPort, messagePort, mapper)
            publisher.pollAndDispatch()

            verify(exactly = 1) { outboxPort.markDead(100L, any()) }
        }
    }

    describe("dispatch — 외부 IO 실패 (백오프)") {
        it("attempts=0 ES 실패 시 markFailed 호출 + nextAttemptAt 가 약 10초 후이다 (5s × 2^1)") {
            val mapper = objectMapperForTest()
            val payload = mapper.writeValueAsString(sampleEvent())
            val row = outbox(id = 1L, channel = ChannelType.ES, payload = payload, attempts = 0)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { searchPort.index(any()) } throws RuntimeException("ES down")

            val nextSlot = slot<Instant>()
            every { outboxPort.markFailed(any(), any(), capture(nextSlot)) } returns Unit

            val before = Instant.now()
            val publisher = OutboxPublisher(outboxPort, searchPort, messagePort, mapper)
            publisher.pollAndDispatch()

            verify(exactly = 1) { outboxPort.markFailed(1L, match { it.contains("ES down") }, any()) }
            verify(exactly = 0) { outboxPort.markPublishedAll(any()) }

            // 5s shl 1 = 10s. 호출 시점 부근 ± 1s 허용.
            val deltaSec = nextSlot.captured.epochSecond - before.epochSecond
            deltaSec.shouldBeBetween(9L, 11L)
        }

        it("attempts=2 KAFKA 실패 시 nextAttemptAt 는 약 40초 후이다 (5s × 2^3)") {
            val mapper = objectMapperForTest()
            val payload = mapper.writeValueAsString(sampleEvent())
            val row = outbox(id = 2L, channel = ChannelType.KAFKA, destination = "logs.raw", payload = payload, attempts = 2)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { messagePort.publishRaw(any()) } throws RuntimeException("kafka down")

            val nextSlot = slot<Instant>()
            every { outboxPort.markFailed(any(), any(), capture(nextSlot)) } returns Unit

            val before = Instant.now()
            val publisher = OutboxPublisher(outboxPort, searchPort, messagePort, mapper)
            publisher.pollAndDispatch()

            val deltaSec = nextSlot.captured.epochSecond - before.epochSecond
            deltaSec.shouldBeBetween(39L, 41L)
        }
    }

    describe("dispatch — MAX_ATTEMPTS 도달") {
        it("attempts=4 행이 실패하면 nextAttempts=5 로 MAX_ATTEMPTS 도달 → markDead 호출") {
            val mapper = objectMapperForTest()
            val payload = mapper.writeValueAsString(sampleEvent())
            val row = outbox(id = 5L, channel = ChannelType.ES, payload = payload, attempts = 4)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { searchPort.index(any()) } throws RuntimeException("permanent failure")

            val publisher = OutboxPublisher(outboxPort, searchPort, messagePort, mapper)
            publisher.pollAndDispatch()

            verify(exactly = 1) { outboxPort.markDead(5L, match { it.contains("permanent failure") }) }
            verify(exactly = 0) { outboxPort.markFailed(any(), any(), any()) }
            verify(exactly = 0) { outboxPort.markPublishedAll(any()) }
        }
    }

    describe("dispatch — 다중 행 처리") {
        it("여러 outbox 가 한 batch 에 있으면 각각 채널별로 처리된다") {
            val mapper = objectMapperForTest()
            val payload = mapper.writeValueAsString(sampleEvent())

            val rows = listOf(
                outbox(id = 1L, channel = ChannelType.ES, payload = payload),
                outbox(id = 2L, channel = ChannelType.KAFKA, destination = "logs.raw", payload = payload),
                outbox(id = 3L, channel = ChannelType.FILE, payload = payload),
            )

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns rows

            val publisher = OutboxPublisher(outboxPort, searchPort, messagePort, mapper)
            publisher.pollAndDispatch()

            verify(exactly = 1) { searchPort.index(any()) }
            verify(exactly = 1) { messagePort.publishRaw(any()) }
            // ES (1L) + KAFKA (2L) 성공 → 한 번의 markPublishedAll 로 일괄 처리.
            verify(exactly = 1) { outboxPort.markPublishedAll(listOf(1L, 2L)) }
            verify(exactly = 1) { outboxPort.markDead(3L, any()) }
        }
    }
})
