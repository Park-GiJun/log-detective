package com.gijun.logdetect.ingest.infrastructure.util

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * ErrorRedactor 단위 테스트.
 *
 * 주의 — Kotest 6.1.0 + Kotlin 2.3 호환성 이슈 (#70) 로 빌드 시 자동 실행되지
 * 않을 수 있다. IDE 에서 개별 실행하여 검증한다.
 */
class ErrorRedactorTest : DescribeSpec({

    describe("IPv4 주소 마스킹") {
        it("단일 IPv4 가 들어오면 ***.***.***.*** 로 치환된다") {
            ErrorRedactor.redact("ES connection refused at 10.0.13.42") shouldBe
                "ES connection refused at ***.***.***.***"
        }

        it("문장 안에 여러 IPv4 가 있어도 모두 마스킹된다") {
            val out = ErrorRedactor.redact("from 192.168.1.1 to 10.0.0.5 failed")
            out.shouldNotContain("192.168.1.1")
            out.shouldNotContain("10.0.0.5")
            out shouldBe "from ***.***.***.*** to ***.***.***.*** failed"
        }
    }

    describe("URL 자격 증명 마스킹") {
        it("user:pass@host 형태의 자격 증명은 ***@host 로 치환된다") {
            val out = ErrorRedactor.redact(
                "auth failed against postgres://admin:s3cret@db.internal:5432/logs",
            )
            out.shouldNotContain("admin")
            out.shouldNotContain("s3cret")
            out.shouldContain("postgres://***@db.internal:5432/logs")
        }

        it("scheme 없이 user:pass@ 만 있으면 마스킹되지 않는다 (오탐 방지)") {
            // 일반 텍스트의 콜론까지 잡아내면 false positive 가 너무 많아지므로 scheme(://) 가 있을 때만 매치한다.
            val out = ErrorRedactor.redact("notice: admin:secret@server is invalid")
            out.shouldContain("admin:secret@server")
        }
    }

    describe("JWT 마스킹") {
        it("eyJ 로 시작하는 3-segment JWT 토큰은 [REDACTED] 로 치환된다") {
            val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NSJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
            val out = ErrorRedactor.redact("Authorization rejected: $jwt at endpoint")
            out.shouldNotContain(jwt)
            out.shouldContain("[REDACTED]")
        }
    }

    describe("쿼리 시크릿 마스킹") {
        it("?token=... 값이 마스킹된다 (key 는 보존)") {
            val out = ErrorRedactor.redact("GET /api/health?token=abc123def&trace=1")
            out shouldBe "GET /api/health?token=[REDACTED]&trace=1"
        }

        it("&api_key=... 값이 마스킹된다 (case-insensitive)") {
            val out = ErrorRedactor.redact("call failed: /v1?ts=123&Api_Key=K9XQ-LMN")
            out.shouldNotContain("K9XQ-LMN")
            out.shouldContain("Api_Key=[REDACTED]")
        }

        it("&password=... 값이 마스킹된다") {
            val out = ErrorRedactor.redact("login url ?u=foo&password=hunter2")
            out.shouldNotContain("hunter2")
            out.shouldContain("password=[REDACTED]")
        }

        it("api-key (하이픈) 도 매칭된다") {
            val out = ErrorRedactor.redact("?api-key=ZZZ next")
            out.shouldContain("api-key=[REDACTED]")
        }
    }

    describe("복합 케이스") {
        it("IP + 자격 증명 + 토큰이 동시에 등장해도 모두 마스킹된다") {
            val raw = "Connection to http://root:1234@10.20.30.40/api?token=abc&x=1 timed out"
            val out = ErrorRedactor.redact(raw)
            out.shouldNotContain("root")
            out.shouldNotContain("10.20.30.40")
            out.shouldNotContain("token=abc")
            out.shouldContain("***@")
            out.shouldContain("***.***.***.***")
            out.shouldContain("token=[REDACTED]")
        }

        it("민감하지 않은 일반 메시지는 그대로 유지된다") {
            val raw = "ES bulk failed: index_not_found_exception"
            ErrorRedactor.redact(raw) shouldBe raw
        }

        it("빈 문자열 입력은 빈 문자열을 반환한다") {
            ErrorRedactor.redact("") shouldBe ""
        }
    }
})
