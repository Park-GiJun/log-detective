package com.gijun.logdetect.ingest.application.handler.command

import com.gijun.logdetect.ingest.application.port.out.ErrorRedactorPort
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort.DeadUpdate
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort.FailureUpdate
import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.enums.OutboxStatus
import com.gijun.logdetect.ingest.domain.model.Outbox
import com.gijun.logdetect.ingest.domain.port.Clock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import java.util.UUID

class DispatchOutboxHandlerTest : DescribeSpec({

    val fixedNow: Instant = Instant.parse("2026-04-26T10:00:00Z")
    fun fixedClock() = Clock { fixedNow }
    fun identityRedactor() = ErrorRedactorPort { it }

    fun outbox(
        id: Long = 1L,
        channel: ChannelType = ChannelType.ES,
        destination: String = "logs-2026.04.26",
        attempts: Int = 0,
        status: OutboxStatus = OutboxStatus.PENDING,
        aggregateId: String = UUID.randomUUID().toString(),
        payload: String = "{}",
    ) = Outbox(
        id = id,
        aggregateId = aggregateId,
        channel = channel,
        destination = destination,
        payload = payload,
        status = status,
        attempts = attempts,
        nextAttemptAt = fixedNow,
        createdAt = fixedNow,
    )

    fun newHandler(
        outboxPort: OutboxPersistencePort,
        searchPort: LogEventSearchPort,
        messagePort: LogEventMessagePort,
        errorRedactor: ErrorRedactorPort = identityRedactor(),
        maxAttempts: Int = 5,
        baseBackoffSec: Long = 5L,
        maxBackoffShift: Int = 6,
        maxErrorLength: Int = 1000,
    ) = DispatchOutboxHandler(
        outboxPersistencePort = outboxPort,
        logEventSearchPort = searchPort,
        logEventMessagePort = messagePort,
        clock = fixedClock(),
        errorRedactor = errorRedactor,
        maxAttempts = maxAttempts,
        baseBackoffSec = baseBackoffSec,
        maxBackoffShift = maxBackoffShift,
        maxErrorLength = maxErrorLength,
    )

    describe("dispatchPending — empty fetch") {
        it("fetchPending 결과가 비어있으면 어떤 외부 port 도 호출하지 않는다") {
            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>()
            every { outboxPort.fetchPending(any()) } returns emptyList()

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            verify(exactly = 0) { outboxPort.markPublishedAll(any()) }
            verify(exactly = 0) { outboxPort.markFailedAll(any()) }
            verify(exactly = 0) { outboxPort.markDeadAll(any()) }
        }
    }

    describe("dispatch — ES 채널 전부 성공") {
        it("ES bulk 성공 시 markPublishedAll 로 일괄 호출한다") {
            val agg = UUID.randomUUID().toString()
            val row = outbox(id = 42L, channel = ChannelType.ES, aggregateId = agg)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { searchPort.indexBulk(any()) } returns
                LogEventSearchPort.BulkResult(successIds = setOf(agg), failures = emptyMap())

            val publishedSlot = slot<List<Long>>()
            every { outboxPort.markPublishedAll(capture(publishedSlot)) } returns Unit

            val summary = newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            publishedSlot.captured shouldContainExactly listOf(42L)
            verify(exactly = 0) { outboxPort.markFailedAll(any()) }
            verify(exactly = 0) { outboxPort.markDeadAll(any()) }
            summary.succeeded shouldBe 1
            summary.total shouldBe 1
        }
    }

    describe("dispatch — KAFKA 채널 전부 성공") {
        it("Kafka bulk 성공 시 markPublishedAll 로 일괄 호출한다") {
            val agg = UUID.randomUUID().toString()
            val row = outbox(id = 7L, channel = ChannelType.KAFKA, destination = "logs.raw", aggregateId = agg)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>()
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { messagePort.publishBulk(any()) } returns
                LogEventMessagePort.BulkResult(successKeys = setOf(agg), failures = emptyMap())

            val publishedSlot = slot<List<Long>>()
            every { outboxPort.markPublishedAll(capture(publishedSlot)) } returns Unit

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            publishedSlot.captured shouldContainExactly listOf(7L)
        }
    }

    describe("dispatch — 미지원 채널 (FILE / OTHERS)") {
        it("FILE 채널은 외부 port 호출 없이 markDeadAll 로 분류된다") {
            val row = outbox(id = 99L, channel = ChannelType.FILE)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)

            val deadSlot = slot<List<DeadUpdate>>()
            every { outboxPort.markDeadAll(capture(deadSlot)) } returns Unit

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            deadSlot.captured.map { it.id } shouldContainExactly listOf(99L)
            deadSlot.captured.first().error.contains("unsupported channel") shouldBe true
            verify(exactly = 0) { searchPort.indexBulk(any()) }
            verify(exactly = 0) { messagePort.publishBulk(any()) }
        }
    }

    describe("dispatch — ES bulk 전체 예외") {
        it("attempts=0 ES 가 RuntimeException 으로 실패하면 markFailedAll 에 nextAttemptAt = now + 10s") {
            val agg = UUID.randomUUID().toString()
            val row = outbox(id = 1L, channel = ChannelType.ES, attempts = 0, aggregateId = agg)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { searchPort.indexBulk(any()) } throws RuntimeException("ES down")

            val failureSlot = slot<List<FailureUpdate>>()
            every { outboxPort.markFailedAll(capture(failureSlot)) } returns Unit

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            failureSlot.captured shouldContainExactly listOf(
                FailureUpdate(id = 1L, error = "ES down", nextAttemptAt = fixedNow.plusSeconds(10)),
            )
            verify(exactly = 0) { outboxPort.markPublishedAll(any()) }
        }
    }

    describe("dispatch — MAX_ATTEMPTS 도달") {
        it("attempts=4 행이 실패하면 nextAttempts=5 로 markDeadAll 호출 (markFailedAll 호출 없음)") {
            val agg = UUID.randomUUID().toString()
            val row = outbox(id = 5L, channel = ChannelType.ES, attempts = 4, aggregateId = agg)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { searchPort.indexBulk(any()) } throws RuntimeException("permanent failure")

            val deadSlot = slot<List<DeadUpdate>>()
            every { outboxPort.markDeadAll(capture(deadSlot)) } returns Unit

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            deadSlot.captured.map { it.id } shouldContainExactly listOf(5L)
            verify(exactly = 0) { outboxPort.markFailedAll(any()) }
            verify(exactly = 0) { outboxPort.markPublishedAll(any()) }
        }
    }

    describe("dispatch — 채널 혼합 (ES + KAFKA + FILE)") {
        it("ES bulk + Kafka bulk + FILE markDeadAll 각각 한 번씩 호출되고 markPublishedAll 은 두 id 를 포함한다") {
            val esAgg = UUID.randomUUID().toString()
            val kafkaAgg = UUID.randomUUID().toString()
            val rows = listOf(
                outbox(id = 1L, channel = ChannelType.ES, aggregateId = esAgg),
                outbox(id = 2L, channel = ChannelType.KAFKA, destination = "logs.raw", aggregateId = kafkaAgg),
                outbox(id = 3L, channel = ChannelType.FILE),
            )

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>()
            every { outboxPort.fetchPending(any()) } returns rows
            every { searchPort.indexBulk(any()) } returns
                LogEventSearchPort.BulkResult(successIds = setOf(esAgg), failures = emptyMap())
            every { messagePort.publishBulk(any()) } returns
                LogEventMessagePort.BulkResult(successKeys = setOf(kafkaAgg), failures = emptyMap())

            val publishedSlot = slot<List<Long>>()
            val deadSlot = slot<List<DeadUpdate>>()
            every { outboxPort.markPublishedAll(capture(publishedSlot)) } returns Unit
            every { outboxPort.markDeadAll(capture(deadSlot)) } returns Unit

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            publishedSlot.captured shouldContainExactlyInAnyOrder listOf(1L, 2L)
            deadSlot.captured.map { it.id } shouldContainExactly listOf(3L)
            verify(exactly = 1) { searchPort.indexBulk(any()) }
            verify(exactly = 1) { messagePort.publishBulk(any()) }
        }
    }

    describe("dispatch — ES bulk 부분 실패") {
        it("ES bulk 가 한 행은 성공, 한 행은 실패 응답을 주면 markPublishedAll + markFailedAll 둘 다 호출된다") {
            val okAgg = UUID.randomUUID().toString()
            val ngAgg = UUID.randomUUID().toString()
            val rows = listOf(
                outbox(id = 11L, channel = ChannelType.ES, aggregateId = okAgg),
                outbox(id = 12L, channel = ChannelType.ES, aggregateId = ngAgg, attempts = 1),
            )

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns rows
            every { searchPort.indexBulk(any()) } returns LogEventSearchPort.BulkResult(
                successIds = setOf(okAgg),
                failures = mapOf(ngAgg to "mapper_parsing_exception"),
            )

            val publishedSlot = slot<List<Long>>()
            val failureSlot = slot<List<FailureUpdate>>()
            every { outboxPort.markPublishedAll(capture(publishedSlot)) } returns Unit
            every { outboxPort.markFailedAll(capture(failureSlot)) } returns Unit

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            publishedSlot.captured shouldContainExactly listOf(11L)
            failureSlot.captured.map { it.id } shouldContainExactly listOf(12L)
            // attempts=1 → nextAttempts=2 → 5s shl 2 = 20s
            failureSlot.captured.first().nextAttemptAt shouldBe fixedNow.plusSeconds(20)
        }
    }

    // ============ 추가 시나리오 (이슈 #94) ============

    describe("dispatch — 동일 채널 partial failure + 일부 MAX_ATTEMPTS 도달") {
        it("ES 부분 실패 결과 중 attempts=4 행은 dead, attempts=1 행은 backoff 로 분류되어 markFailedAll 과 markDeadAll 이 동시에 호출된다") {
            val okAgg = UUID.randomUUID().toString()
            val retryAgg = UUID.randomUUID().toString()
            val deadAgg = UUID.randomUUID().toString()
            val rows = listOf(
                outbox(id = 21L, channel = ChannelType.ES, aggregateId = okAgg, attempts = 0),
                // 부분 실패 + 재시도 가능
                outbox(id = 22L, channel = ChannelType.ES, aggregateId = retryAgg, attempts = 1),
                // 부분 실패 + 다음 시도가 maxAttempts 도달
                outbox(id = 23L, channel = ChannelType.ES, aggregateId = deadAgg, attempts = 4),
            )

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns rows
            every { searchPort.indexBulk(any()) } returns LogEventSearchPort.BulkResult(
                successIds = setOf(okAgg),
                failures = mapOf(
                    retryAgg to "es-temporary",
                    deadAgg to "es-temporary",
                ),
            )

            val publishedSlot = slot<List<Long>>()
            val failureSlot = slot<List<FailureUpdate>>()
            val deadSlot = slot<List<DeadUpdate>>()
            every { outboxPort.markPublishedAll(capture(publishedSlot)) } returns Unit
            every { outboxPort.markFailedAll(capture(failureSlot)) } returns Unit
            every { outboxPort.markDeadAll(capture(deadSlot)) } returns Unit

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            publishedSlot.captured shouldContainExactly listOf(21L)
            failureSlot.captured.map { it.id } shouldContainExactly listOf(22L)
            deadSlot.captured.map { it.id } shouldContainExactly listOf(23L)
            // 22L: attempts=1 → nextAttempts=2 → 5s shl 2 = 20s
            failureSlot.captured.first().nextAttemptAt shouldBe fixedNow.plusSeconds(20)
            // 세 mark* 메서드가 모두 정확히 1회 호출
            verify(exactly = 1) { outboxPort.markPublishedAll(any()) }
            verify(exactly = 1) { outboxPort.markFailedAll(any()) }
            verify(exactly = 1) { outboxPort.markDeadAll(any()) }
        }
    }

    describe("dispatch — Kafka bulk 부분 실패") {
        it("Kafka bulk 결과의 successKeys 와 failures 가 각각 markPublishedAll / markFailedAll 로 분리되어 호출된다") {
            val okKey = UUID.randomUUID().toString()
            val ngKey = UUID.randomUUID().toString()
            val rows = listOf(
                outbox(id = 31L, channel = ChannelType.KAFKA, destination = "logs.raw", aggregateId = okKey),
                outbox(id = 32L, channel = ChannelType.KAFKA, destination = "logs.raw", aggregateId = ngKey, attempts = 2),
            )

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>(relaxed = true)
            val messagePort = mockk<LogEventMessagePort>()
            every { outboxPort.fetchPending(any()) } returns rows
            every { messagePort.publishBulk(any()) } returns LogEventMessagePort.BulkResult(
                successKeys = setOf(okKey),
                failures = mapOf(ngKey to "TimeoutException"),
            )

            val publishedSlot = slot<List<Long>>()
            val failureSlot = slot<List<FailureUpdate>>()
            every { outboxPort.markPublishedAll(capture(publishedSlot)) } returns Unit
            every { outboxPort.markFailedAll(capture(failureSlot)) } returns Unit

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            publishedSlot.captured shouldContainExactly listOf(31L)
            failureSlot.captured.map { it.id } shouldContainExactly listOf(32L)
            failureSlot.captured.first().error shouldBe "TimeoutException"
            // attempts=2 → nextAttempts=3 → 5s shl 3 = 40s
            failureSlot.captured.first().nextAttemptAt shouldBe fixedNow.plusSeconds(40)
            verify(exactly = 0) { outboxPort.markDeadAll(any()) }
        }
    }

    describe("dispatch — ErrorRedactor 가 raw error 를 마스킹한 결과로 FailureUpdate.error 에 저장한다") {
        it("custom redactor 를 주입하면 indexBulk 예외 메시지가 redact 결과로 치환되어 last_error 에 들어간다") {
            val agg = UUID.randomUUID().toString()
            val row = outbox(id = 41L, channel = ChannelType.ES, aggregateId = agg, attempts = 0)
            // IPv4 주소를 마스킹하는 redactor — ErrorRedactor 와 호환되는 정책
            val maskingRedactor = ErrorRedactorPort { input ->
                input.replace(Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b"""), "***.***.***.***")
            }

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { searchPort.indexBulk(any()) } throws RuntimeException("ES connection refused at 10.0.13.42:9200")

            val failureSlot = slot<List<FailureUpdate>>()
            every { outboxPort.markFailedAll(capture(failureSlot)) } returns Unit

            newHandler(
                outboxPort,
                searchPort,
                messagePort,
                errorRedactor = maskingRedactor,
            ).dispatchPending()

            // raw error 의 IPv4 가 마스킹된 채 저장되어야 한다 — identity redactor 에서는 검증할 수 없는 항목.
            failureSlot.captured.first().error shouldBe "ES connection refused at ***.***.***.***:9200"
        }
    }

    describe("dispatch — maxErrorLength 초과 메시지 take") {
        it("redact 후 길이가 maxErrorLength 를 초과하면 잘려서 FailureUpdate.error 에 저장된다") {
            val agg = UUID.randomUUID().toString()
            val row = outbox(id = 51L, channel = ChannelType.ES, aggregateId = agg, attempts = 0)
            // 1001 자 메시지 — default 1000 자를 1 초과
            val longMessage = "x".repeat(1001)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { searchPort.indexBulk(any()) } throws RuntimeException(longMessage)

            val failureSlot = slot<List<FailureUpdate>>()
            every { outboxPort.markFailedAll(capture(failureSlot)) } returns Unit

            newHandler(outboxPort, searchPort, messagePort).dispatchPending()

            failureSlot.captured.first().error.length shouldBe 1000
            failureSlot.captured.first().error shouldBe "x".repeat(1000)
        }
    }

    describe("dispatch — backoff 상한 (maxBackoffShift)") {
        it("attempts 가 maxBackoffShift 를 초과해도 backoff 가 5s × 2^maxBackoffShift = 320s 로 cap 된다") {
            val agg = UUID.randomUUID().toString()
            // attempts=10 — nextAttempts=11 이지만 maxAttempts=20 으로 dead 회피.
            // shift 는 maxBackoffShift(=6) 으로 coerceAtMost 되어 5s shl 6 = 320s 가 되어야 한다.
            val row = outbox(id = 61L, channel = ChannelType.ES, aggregateId = agg, attempts = 10)

            val outboxPort = mockk<OutboxPersistencePort>(relaxed = true)
            val searchPort = mockk<LogEventSearchPort>()
            val messagePort = mockk<LogEventMessagePort>(relaxed = true)
            every { outboxPort.fetchPending(any()) } returns listOf(row)
            every { searchPort.indexBulk(any()) } throws RuntimeException("transient")

            val failureSlot = slot<List<FailureUpdate>>()
            every { outboxPort.markFailedAll(capture(failureSlot)) } returns Unit

            newHandler(
                outboxPort,
                searchPort,
                messagePort,
                maxAttempts = 20,
                baseBackoffSec = 5L,
                maxBackoffShift = 6,
            ).dispatchPending()

            // 5s shl 6 = 320s — backoff 가 더 길어지지 않는다.
            failureSlot.captured.first().nextAttemptAt shouldBe fixedNow.plusSeconds(320)
        }
    }
})
