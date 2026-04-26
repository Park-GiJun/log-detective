package com.gijun.logdetect.generator.infrastructure.adapter.out.client

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec

class SsrfGuardTest : DescribeSpec({

    describe("스키마 검증") {
        it("file:// 스키마는 차단") {
            shouldThrow<SsrfViolationException> {
                SsrfGuard.validateUrl("file:///etc/passwd")
            }
        }
        it("gopher:// 스키마는 차단") {
            shouldThrow<SsrfViolationException> {
                SsrfGuard.validateUrl("gopher://example.com/")
            }
        }
        it("잘못된 URL 형식은 차단") {
            shouldThrow<SsrfViolationException> {
                SsrfGuard.validateUrl("::not a url::")
            }
        }
    }

    describe("사설/루프백 차단 (allowPrivateNetwork=false)") {
        it("127.0.0.1 차단") {
            shouldThrow<SsrfViolationException> {
                SsrfGuard.validateUrl("http://127.0.0.1/")
            }
        }
        it("localhost 차단") {
            shouldThrow<SsrfViolationException> {
                SsrfGuard.validateUrl("http://localhost:8080/")
            }
        }
        it("10.0.0.1 차단 (사설망)") {
            shouldThrow<SsrfViolationException> {
                SsrfGuard.validateUrl("http://10.0.0.1/")
            }
        }
        it("192.168.1.1 차단") {
            shouldThrow<SsrfViolationException> {
                SsrfGuard.validateUrl("http://192.168.1.1/")
            }
        }
        it("172.16.0.1 차단") {
            shouldThrow<SsrfViolationException> {
                SsrfGuard.validateUrl("http://172.16.0.1/")
            }
        }
    }

    describe("메타데이터 엔드포인트는 항상 차단") {
        it("169.254.169.254 — allowPrivateNetwork=true 여도 차단") {
            shouldThrow<SsrfViolationException> {
                SsrfGuard.validateUrl(
                    url = "http://169.254.169.254/latest/meta-data/",
                    allowPrivateNetwork = true,
                )
            }
        }
    }

    describe("allowPrivateNetwork=true") {
        it("127.0.0.1 통과") {
            shouldNotThrow<SsrfViolationException> {
                SsrfGuard.validateUrl(
                    url = "http://127.0.0.1:8080/api",
                    allowPrivateNetwork = true,
                )
            }
        }
        it("10.x 통과") {
            shouldNotThrow<SsrfViolationException> {
                SsrfGuard.validateUrl(
                    url = "http://10.0.0.5/api",
                    allowPrivateNetwork = true,
                )
            }
        }
    }

    describe("allowedHosts 화이트리스트") {
        it("화이트리스트 일치 시 사설망이어도 통과 (DNS 해석 스킵)") {
            shouldNotThrow<SsrfViolationException> {
                SsrfGuard.validateUrl(
                    url = "http://internal-ingest/api",
                    allowedHosts = listOf("internal-ingest"),
                    allowPrivateNetwork = false,
                )
            }
        }
        it("화이트리스트 대소문자 무시") {
            shouldNotThrow<SsrfViolationException> {
                SsrfGuard.validateUrl(
                    url = "http://INTERNAL-INGEST/api",
                    allowedHosts = listOf("internal-ingest"),
                )
            }
        }
    }
})
