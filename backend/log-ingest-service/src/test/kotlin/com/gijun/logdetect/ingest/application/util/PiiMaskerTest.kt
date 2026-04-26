package com.gijun.logdetect.ingest.application.util

import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

/**
 * PiiMasker 단위 테스트.
 *
 * 주의 — Kotest 6.1.0 + Kotlin 2.3 호환성 이슈로 빌드 시 자동 실행되지 않을 수 있다.
 * IDE 에서 개별 실행하여 검증한다.
 */
class PiiMaskerTest : DescribeSpec({

    describe("maskIp") {
        it("정상 IPv4 의 마지막 옥텟이 * 로 치환된다") {
            PiiMasker.maskIp("10.0.13.42") shouldBe "10.0.13.*"
        }

        it("0.0.0.0 같은 경계 값도 정상 마스킹된다") {
            PiiMasker.maskIp("0.0.0.0") shouldBe "0.0.0.*"
        }

        it("null 입력은 null 반환") {
            PiiMasker.maskIp(null) shouldBe null
        }

        it("빈 문자열 입력은 그대로 반환") {
            PiiMasker.maskIp("") shouldBe ""
        }

        it("IPv6 / 비표준 포맷은 정규식 미스매치 시 그대로 반환된다") {
            // 마스킹 정책 검토 후 IPv6 지원이 결정될 때까지 보수적으로 그대로 둔다.
            PiiMasker.maskIp("::1") shouldBe "::1"
            PiiMasker.maskIp("not-an-ip") shouldBe "not-an-ip"
        }
    }

    describe("maskUserId") {
        it("길이 > 4 인 식별자는 앞 4글자 보존, 나머지는 *") {
            PiiMasker.maskUserId("user-123-1234") shouldBe "user*********"
        }

        it("길이 ≤ 4 인 짧은 식별자는 전부 *") {
            PiiMasker.maskUserId("u") shouldBe "*"
            PiiMasker.maskUserId("ab") shouldBe "**"
            PiiMasker.maskUserId("abcd") shouldBe "****"
        }

        it("null / 빈 문자열은 그대로 반환") {
            PiiMasker.maskUserId(null) shouldBe null
            PiiMasker.maskUserId("") shouldBe ""
        }
    }

    describe("mask(LogEvent)") {
        it("ip 와 userId 만 마스킹하고 나머지 필드는 보존한다") {
            val original = LogEvent(
                eventId = UUID.randomUUID(),
                source = "test-source",
                level = LogLevel.WARN,
                message = "auth failure",
                timestamp = Instant.parse("2026-04-26T10:00:00Z"),
                host = "host-1",
                ip = "192.168.1.10",
                userId = "user-12345",
                attributes = mapOf("k" to "v"),
            )

            val masked = PiiMasker.mask(original)

            masked.ip shouldBe "192.168.1.*"
            masked.userId shouldBe "user******"
            masked.eventId shouldBe original.eventId
            masked.source shouldBe original.source
            masked.level shouldBe original.level
            masked.message shouldBe original.message
            masked.timestamp shouldBe original.timestamp
            masked.host shouldBe original.host
            masked.attributes shouldBe original.attributes
        }

        it("ip / userId 가 null 인 LogEvent 는 null 그대로 유지") {
            val original = LogEvent(
                eventId = UUID.randomUUID(),
                source = "s",
                level = LogLevel.INFO,
                message = "m",
                timestamp = Instant.parse("2026-04-26T10:00:00Z"),
            )

            val masked = PiiMasker.mask(original)

            masked.ip shouldBe null
            masked.userId shouldBe null
        }
    }
})
