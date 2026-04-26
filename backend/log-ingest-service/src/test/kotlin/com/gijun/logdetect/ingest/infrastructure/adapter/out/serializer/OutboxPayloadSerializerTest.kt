package com.gijun.logdetect.ingest.infrastructure.adapter.out.serializer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant
import java.util.UUID

/**
 * OutboxPayloadSerializer 단위 테스트 — PII 마스킹 토글 동작을 검증한다 (이슈 #49).
 *
 * 토글이 OFF (default) 면 LogEvent 가 평문 그대로 직렬화되고,
 * ON 이면 PiiMasker 가 적용되어 ip / userId 가 마스킹된다.
 */
class OutboxPayloadSerializerTest : DescribeSpec({

    fun mapper(): ObjectMapper =
        jacksonObjectMapper().registerModule(JavaTimeModule())

    fun sampleEvent() = LogEvent(
        eventId = UUID.randomUUID(),
        source = "s",
        level = LogLevel.INFO,
        message = "auth",
        timestamp = Instant.parse("2026-04-26T10:00:00Z"),
        ip = "192.168.1.10",
        userId = "user-12345",
    )

    describe("serialize") {
        it("piiMaskPayload=false (default) 면 ip / userId 가 평문 그대로 직렬화된다") {
            val serializer = OutboxPayloadSerializer(mapper(), piiMaskPayload = false)

            val json = serializer.serialize(sampleEvent())

            json shouldContain "192.168.1.10"
            json shouldContain "user-12345"
        }

        it("piiMaskPayload=true 면 ip 의 마지막 옥텟 / userId 의 prefix 만 보존되어 마스킹된다") {
            val serializer = OutboxPayloadSerializer(mapper(), piiMaskPayload = true)

            val json = serializer.serialize(sampleEvent())

            json shouldNotContain "192.168.1.10"
            json shouldNotContain "user-12345"
            // PiiMasker 정책: IP 의 마지막 옥텟은 *, userId 는 앞 4자 보존 후 나머지 *
            json shouldContain "192.168.1.*"
            json shouldContain "user******"
        }
    }
})
