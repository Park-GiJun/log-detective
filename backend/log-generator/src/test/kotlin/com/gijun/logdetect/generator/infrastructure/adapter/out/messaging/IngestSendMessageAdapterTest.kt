package com.gijun.logdetect.generator.infrastructure.adapter.out.messaging

import com.gijun.logdetect.generator.domain.model.LogEvent
import com.gijun.logdetect.generator.infrastructure.adapter.out.messaging.dto.IngestSendMessage
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

private const val TOPIC = "logs.raw"

class IngestSendMessageAdapterTest : DescribeSpec({

    fun sampleEvent() = LogEvent(
        id = 1L,
        transactionId = "tx-1",
        source = "src",
        level = "INFO",
        message = "msg",
        timestamp = Instant.parse("2026-04-26T10:00:00Z"),
        host = "host",
        ip = "10.0.0.1",
        userId = 7L,
        attributes = mapOf("k" to "v"),
    )

    @Suppress("UNCHECKED_CAST")
    fun mockTemplate(): KafkaTemplate<String, IngestSendMessage> =
        mockk<KafkaTemplate<String, IngestSendMessage>>()

    describe("send — 정상 경로") {
        it("KafkaTemplate.send().get() 정상 반환 시 true") {
            val template = mockTemplate()
            val sendResult = mockk<SendResult<String, IngestSendMessage>>(relaxed = true)
            val future: CompletableFuture<SendResult<String, IngestSendMessage>> =
                CompletableFuture.completedFuture(sendResult)
            every { template.send(any<String>(), any<String>(), any<IngestSendMessage>()) } returns future

            val adapter = IngestSendMessageAdapter(template, TOPIC)
            adapter.send(sampleEvent()) shouldBe true

            verify(exactly = 1) { template.send(TOPIC, "tx-1", any()) }
        }

        it("LogEvent → IngestSendMessage 변환이 정확히 수행되어 transactionId / source / level 이 보존된다") {
            val template = mockTemplate()
            val sendResult = mockk<SendResult<String, IngestSendMessage>>(relaxed = true)
            every { template.send(any<String>(), any<String>(), any<IngestSendMessage>()) } returns
                CompletableFuture.completedFuture(sendResult)

            val captured = slot<IngestSendMessage>()
            every { template.send(any<String>(), any<String>(), capture(captured)) } returns
                CompletableFuture.completedFuture(sendResult)

            val adapter = IngestSendMessageAdapter(template, TOPIC)
            adapter.send(sampleEvent()) shouldBe true

            captured.captured.transactionId shouldBe "tx-1"
            captured.captured.source shouldBe "src"
            captured.captured.level shouldBe "INFO"
            captured.captured.userId shouldBe 7L
        }
    }

    describe("send — Kafka 예외") {
        it("template.send 자체가 예외를 던지면 catch 되어 false") {
            val template = mockTemplate()
            every { template.send(any<String>(), any<String>(), any<IngestSendMessage>()) } throws
                IllegalStateException("producer not ready")

            val adapter = IngestSendMessageAdapter(template, TOPIC)
            adapter.send(sampleEvent()) shouldBe false
        }

        it("Future.get() 이 ExecutionException (broker 다운 등) 으로 실패하면 false") {
            val template = mockTemplate()
            val failedFuture = CompletableFuture<SendResult<String, IngestSendMessage>>().apply {
                completeExceptionally(ExecutionException("broker unavailable", RuntimeException()))
            }
            every { template.send(any<String>(), any<String>(), any<IngestSendMessage>()) } returns failedFuture

            val adapter = IngestSendMessageAdapter(template, TOPIC)
            adapter.send(sampleEvent()) shouldBe false
        }

        it("Future.get() 이 timeout / interrupt 류 예외로 실패해도 false") {
            val template = mockTemplate()
            val failedFuture = CompletableFuture<SendResult<String, IngestSendMessage>>().apply {
                completeExceptionally(RuntimeException("timeout"))
            }
            every { template.send(any<String>(), any<String>(), any<IngestSendMessage>()) } returns failedFuture

            val adapter = IngestSendMessageAdapter(template, TOPIC)
            adapter.send(sampleEvent()) shouldBe false
        }
    }
})
