package com.gijun.logdetect.ingest.infrastructure.adapter.out.serializer

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.util.PiiMode
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant
import java.util.UUID

/**
 * OutboxPayloadSerializer 단위 테스트 — PII 마스킹 토글 동작을 검증한다 (이슈 #49 / #88).
 *
 * 토글이 OFF (default) 면 LogEvent 가 평문 그대로 직렬화되고,
 * ON 이면 PiiMasker 가 적용되어 ip / userId 가 모드 (MASK / HASH) 에 따라 변환된다.
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
            val serializer = OutboxPayloadSerializer(
                mapper(),
                piiMaskPayload = false,
                piiMode = PiiMode.MASK,
                piiSalt = "",
            )

            val json = serializer.serialize(sampleEvent())

            json shouldContain "192.168.1.10"
            json shouldContain "user-12345"
        }

        it("piiMaskPayload=true + MASK 면 ip 는 /16, userId 는 길이 보존 * 로 직렬화된다") {
            val serializer = OutboxPayloadSerializer(
                mapper(),
                piiMaskPayload = true,
                piiMode = PiiMode.MASK,
                piiSalt = "",
            )

            val json = serializer.serialize(sampleEvent())

            json shouldNotContain "192.168.1.10"
            json shouldNotContain "user-12345"
            // 강화된 정책: IPv4 /16 (`A.B.*.*`), userId 길이 보존 *
            json shouldContain "192.168.*.*"
            json shouldContain "**********"
        }

        it("piiMaskPayload=true + HASH 면 ip / userId 가 HMAC 해시 (h$) 로 직렬화된다") {
            val serializer = OutboxPayloadSerializer(
                mapper(),
                piiMaskPayload = true,
                piiMode = PiiMode.HASH,
                piiSalt = "test-salt",
            )

            val json = serializer.serialize(sampleEvent())

            json shouldNotContain "192.168.1.10"
            json shouldNotContain "user-12345"
            json shouldContain "h$"
        }
    }
})
