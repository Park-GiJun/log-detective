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

    val redactor = ErrorRedactor()

    describe("IPv4 주소 마스킹") {
        it("단일 IPv4 가 들어오면 ***.***.***.*** 로 치환된다") {
            redactor.redact("ES connection refused at 10.0.13.42") shouldBe
                "ES connection refused at ***.***.***.***"
        }

        it("문장 안에 여러 IPv4 가 있어도 모두 마스킹된다") {
            val out = redactor.redact("from 192.168.1.1 to 10.0.0.5 failed")
            out.shouldNotContain("192.168.1.1")
            out.shouldNotContain("10.0.0.5")
            out shouldBe "from ***.***.***.*** to ***.***.***.*** failed"
        }
    }

    describe("URL 자격 증명 마스킹") {
        it("user:pass@host 형태의 자격 증명은 ***@host 로 치환된다") {
            val out = redactor.redact(
                "auth failed against postgres://admin:s3cret@db.internal:5432/logs",
            )
            out.shouldNotContain("admin")
            out.shouldNotContain("s3cret")
            out.shouldContain("postgres://***@db.internal:5432/logs")
        }

        it("scheme 없이 user:pass@ 만 있으면 마스킹되지 않는다 (오탐 방지)") {
            // 일반 텍스트의 콜론까지 잡아내면 false positive 가 너무 많아지므로 scheme(://) 가 있을 때만 매치한다.
            val out = redactor.redact("notice: admin:secret@server is invalid")
            out.shouldContain("admin:secret@server")
        }
    }

    describe("JWT 마스킹") {
        it("eyJ 로 시작하는 3-segment JWT 토큰은 [REDACTED-JWT] 로 치환된다") {
            val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NSJ9.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
            val out = redactor.redact("token rejected: $jwt at endpoint")
            out.shouldNotContain(jwt)
            out.shouldContain("[REDACTED-JWT]")
        }
    }

    describe("쿼리 시크릿 마스킹") {
        it("?token=... 값이 마스킹된다 (key 는 보존)") {
            val out = redactor.redact("GET /api/health?token=abc123def&trace=1")
            out shouldBe "GET /api/health?token=[REDACTED]&trace=1"
        }

        it("&api_key=... 값이 마스킹된다 (case-insensitive)") {
            val out = redactor.redact("call failed: /v1?ts=123&Api_Key=K9XQ-LMN")
            out.shouldNotContain("K9XQ-LMN")
            out.shouldContain("Api_Key=[REDACTED]")
        }

        it("&password=... 값이 마스킹된다") {
            val out = redactor.redact("login url ?u=foo&password=hunter2")
            out.shouldNotContain("hunter2")
            out.shouldContain("password=[REDACTED]")
        }

        it("api-key (하이픈) 도 매칭된다") {
            val out = redactor.redact("?api-key=ZZZ next")
            out.shouldContain("api-key=[REDACTED]")
        }
    }

    describe("AWS Access Key ID 마스킹 (#87)") {
        it("AKIA prefix + 16자 대문자/숫자 패턴은 [REDACTED-AWS-AK] 로 치환된다") {
            val out = redactor.redact("S3 client error: AKIAIOSFODNN7EXAMPLE was rejected")
            out.shouldNotContain("AKIAIOSFODNN7EXAMPLE")
            out.shouldContain("[REDACTED-AWS-AK]")
        }

        it("AKIA 가 아닌 비슷한 prefix 는 매칭되지 않는다 (false positive 방지)") {
            val out = redactor.redact("error code: AKIBNOTACCESSKEY12X")
            out.shouldContain("AKIBNOTACCESSKEY12X")
        }
    }

    describe("AWS Secret Access Key 마스킹 (#87)") {
        it("aws_secret_access_key=... 형태는 값이 마스킹된다") {
            // AWS Secret Access Key 는 정확히 40자 — 테스트 값도 정확히 40자로 둔다.
            val secret = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
            val raw = "config: aws_secret_access_key=$secret fail"
            val out = redactor.redact(raw)
            out.shouldNotContain(secret)
            out.shouldContain("[REDACTED-AWS-SK]")
        }

        it("secret_key= prefix 도 매칭된다") {
            val secret = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789AbCd"
            val raw = "secret_key=$secret dump"
            val out = redactor.redact(raw)
            out.shouldNotContain(secret)
            out.shouldContain("[REDACTED-AWS-SK]")
        }
    }

    describe("GCP API Key 마스킹 (#87)") {
        it("AIza prefix + 35자 패턴은 [REDACTED-GCP] 로 치환된다") {
            // AIza + 정확히 35자.
            val key = "AIzaSyA-1234567890abcdefghijklmnopqrstu"
            val out = redactor.redact("gcp call failed with key $key invalid")
            out.shouldNotContain(key)
            out.shouldContain("[REDACTED-GCP]")
        }
    }

    describe("Authorization 헤더 마스킹 (#87)") {
        it("Bearer 토큰 값이 마스킹된다 (Bearer 키워드 보존)") {
            val out = redactor.redact("401: Bearer eyJhbGciOiJIUzI1NiJ9.foo.bar from header")
            out.shouldNotContain("eyJhbGciOiJIUzI1NiJ9.foo.bar")
            out.shouldContain("Bearer [REDACTED]")
        }

        it("Basic 인증 값이 마스킹된다") {
            val out = redactor.redact("got Basic dXNlcjpwYXNz== while connecting")
            out.shouldNotContain("dXNlcjpwYXNz")
            out.shouldContain("Basic [REDACTED]")
        }

        it("Authorization: <token> 헤더 라인이 마스킹된다 (case-insensitive)") {
            val out = redactor.redact("Authorization: secrettoken12345abcdef from kafka client")
            out.shouldNotContain("secrettoken12345abcdef")
            out.shouldContain("Authorization [REDACTED]")
        }
    }

    describe("주민등록번호 마스킹 (#87)") {
        it("YYMMDD-NNNNNNN 패턴은 [REDACTED-RRN] 로 치환된다") {
            val out = redactor.redact("user record dump: 900101-1234567 invalid")
            out.shouldNotContain("900101-1234567")
            out.shouldContain("[REDACTED-RRN]")
        }

        it("8번대(외국인등록번호 외) 시작은 매칭되지 않아 false positive 를 줄인다") {
            // 주민번호 두 번째 그룹의 첫 글자는 1~4 (남/녀 + 출생연도) — 5~8 은 외국인 등록번호 등 별도 정책이라 본 패턴은 보수적으로 1~4 만.
            val out = redactor.redact("not rrn: 900101-9999999 should not match")
            out.shouldContain("900101-9999999")
        }
    }

    describe("이메일 마스킹 (#87)") {
        it("일반 이메일은 [REDACTED-EMAIL] 로 치환된다") {
            val out = redactor.redact("contact admin@example.com for help")
            out.shouldNotContain("admin@example.com")
            out.shouldContain("[REDACTED-EMAIL]")
        }

        it("플러스 / 점이 포함된 이메일도 매칭된다") {
            val out = redactor.redact("from user.name+tag@sub.example.co.kr to bounce")
            out.shouldNotContain("user.name+tag@sub.example.co.kr")
            out.shouldContain("[REDACTED-EMAIL]")
        }
    }

    describe("전화번호 마스킹 (#87)") {
        it("010-1234-5678 형태는 [REDACTED-PHONE] 로 치환된다") {
            val out = redactor.redact("phone 010-1234-5678 not reachable")
            out.shouldNotContain("010-1234-5678")
            out.shouldContain("[REDACTED-PHONE]")
        }

        it("하이픈 없는 01012345678 도 매칭된다") {
            val out = redactor.redact("dial 01012345678 timeout")
            out.shouldNotContain("01012345678")
            out.shouldContain("[REDACTED-PHONE]")
        }

        it("02-345-6789 (지역번호) 도 매칭된다") {
            val out = redactor.redact("call 02-345-6789 hangup")
            out.shouldNotContain("02-345-6789")
            out.shouldContain("[REDACTED-PHONE]")
        }
    }

    describe("복합 케이스") {
        it("IP + 자격 증명 + 토큰이 동시에 등장해도 모두 마스킹된다") {
            val raw = "Connection to http://root:1234@10.20.30.40/api?token=abc&x=1 timed out"
            val out = redactor.redact(raw)
            out.shouldNotContain("root")
            out.shouldNotContain("10.20.30.40")
            out.shouldNotContain("token=abc")
            out.shouldContain("***@")
            out.shouldContain("***.***.***.***")
            out.shouldContain("token=[REDACTED]")
        }

        it("AWS 키 + 이메일 + 전화 + Bearer 가 한 메시지에 섞여도 모두 마스킹된다") {
            val raw = "alert from admin@corp.io 010-9999-1111 key=AKIAIOSFODNN7EXAMPLE Bearer xyz.abc.def"
            val out = redactor.redact(raw)
            out.shouldNotContain("admin@corp.io")
            out.shouldNotContain("010-9999-1111")
            out.shouldNotContain("AKIAIOSFODNN7EXAMPLE")
            out.shouldNotContain("xyz.abc.def")
            out.shouldContain("[REDACTED-EMAIL]")
            out.shouldContain("[REDACTED-PHONE]")
            out.shouldContain("[REDACTED-AWS-AK]")
            out.shouldContain("Bearer [REDACTED]")
        }

        it("민감하지 않은 일반 메시지는 그대로 유지된다") {
            val raw = "ES bulk failed: index_not_found_exception"
            redactor.redact(raw) shouldBe raw
        }

        it("빈 문자열 입력은 빈 문자열을 반환한다") {
            redactor.redact("") shouldBe ""
        }
    }
})
