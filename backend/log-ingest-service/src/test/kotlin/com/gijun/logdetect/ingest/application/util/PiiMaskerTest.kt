package com.gijun.logdetect.ingest.application.util

import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.time.Instant
import java.util.UUID

/**
 * PiiMasker 단위 테스트.
 *
 * 이슈 #88 강화 후 정책:
 * - IPv4 → `A.B.*.*` (`/16`), userId → 길이 보존 `*` (prefix 미노출)
 * - HASH 모드 → HMAC-SHA256(salt) deterministic pseudonym
 */
class PiiMaskerTest : DescribeSpec({

    describe("maskIp / MASK 모드") {
        it("정상 IPv4 는 마지막 두 옥텟이 * 로 치환된다 (/16)") {
            PiiMasker.maskIp("10.0.13.42") shouldBe "10.0.*.*"
            PiiMasker.maskIp("192.168.1.10") shouldBe "192.168.*.*"
        }

        it("0.0.0.0 같은 경계 값도 정상 마스킹된다") {
            PiiMasker.maskIp("0.0.0.0") shouldBe "0.0.*.*"
        }

        it("IPv6 는 앞 64bit (4그룹) 만 보존하고 뒤 64bit 는 *") {
            // 풀 표기
            PiiMasker.maskIp("2001:db8:85a3:1:0:0:0:1") shouldBe "2001:db8:85a3:1:*:*:*:*"
            // :: 압축 표기 — InetAddress 가 정규화하여 동일 결과
            PiiMasker.maskIp("2001:db8:85a3:1::1") shouldBe "2001:db8:85a3:1:*:*:*:*"
            // loopback ::1 → 0:0:0:0:*:*:*:*
            PiiMasker.maskIp("::1") shouldBe "0:0:0:0:*:*:*:*"
        }

        it("null / 빈 문자열은 그대로 반환") {
            PiiMasker.maskIp(null) shouldBe null
            PiiMasker.maskIp("") shouldBe ""
        }

        it("인식 불가 포맷은 그대로 반환된다") {
            PiiMasker.maskIp("not-an-ip") shouldBe "not-an-ip"
        }
    }

    describe("maskIp / HASH 모드") {
        it("동일 입력 + 동일 salt 는 deterministic 하게 동일 해시를 반환한다") {
            val salt = "test-salt"
            val a = PiiMasker.maskIp("10.0.13.42", PiiMode.HASH, salt)
            val b = PiiMasker.maskIp("10.0.13.42", PiiMode.HASH, salt)
            a shouldBe b
        }

        it("salt 가 다르면 동일 입력이라도 다른 해시가 나온다") {
            val v1 = PiiMasker.maskIp("10.0.13.42", PiiMode.HASH, "salt-1")
            val v2 = PiiMasker.maskIp("10.0.13.42", PiiMode.HASH, "salt-2")
            (v1 == v2) shouldBe false
        }

        it("HMAC 결과는 h$ prefix + 32자 hex 형태") {
            val v = PiiMasker.maskIp("10.0.13.42", PiiMode.HASH, "s")!!
            v shouldStartWith "h$"
            v.length shouldBe 2 + 32
        }

        it("IPv6 도 동일하게 HMAC 처리된다") {
            val v = PiiMasker.maskIp("2001:db8::1", PiiMode.HASH, "s")!!
            v shouldStartWith "h$"
        }
    }

    describe("maskUserId / MASK 모드") {
        it("길이 보존하여 전부 * 로 치환한다 (prefix 미노출)") {
            PiiMasker.maskUserId("user-123-1234") shouldBe "*".repeat("user-123-1234".length)
            PiiMasker.maskUserId("u") shouldBe "*"
            PiiMasker.maskUserId("abcd") shouldBe "****"
        }

        it("null / 빈 문자열은 그대로 반환") {
            PiiMasker.maskUserId(null) shouldBe null
            PiiMasker.maskUserId("") shouldBe ""
        }
    }

    describe("maskUserId / HASH 모드") {
        it("deterministic pseudonym 을 반환한다") {
            val salt = "k"
            val a = PiiMasker.maskUserId("user-12345", PiiMode.HASH, salt)
            val b = PiiMasker.maskUserId("user-12345", PiiMode.HASH, salt)
            a shouldBe b
            a!! shouldStartWith "h$"
        }
    }

    describe("mask(LogEvent)") {
        it("MASK 모드 — ip 와 userId 만 마스킹하고 나머지 필드는 보존한다") {
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

            masked.ip shouldBe "192.168.*.*"
            masked.userId shouldBe "*".repeat("user-12345".length)
            masked.eventId shouldBe original.eventId
            masked.source shouldBe original.source
            masked.level shouldBe original.level
            masked.message shouldBe original.message
            masked.timestamp shouldBe original.timestamp
            masked.host shouldBe original.host
            masked.attributes shouldBe original.attributes
        }

        it("HASH 모드 — ip / userId 가 HMAC 해시로 치환된다") {
            val original = LogEvent(
                eventId = UUID.randomUUID(),
                source = "s",
                level = LogLevel.INFO,
                message = "m",
                timestamp = Instant.parse("2026-04-26T10:00:00Z"),
                ip = "192.168.1.10",
                userId = "user-12345",
            )

            val masked = PiiMasker.mask(original, PiiMode.HASH, "salt")

            masked.ip!! shouldStartWith "h$"
            masked.userId!! shouldStartWith "h$"
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
