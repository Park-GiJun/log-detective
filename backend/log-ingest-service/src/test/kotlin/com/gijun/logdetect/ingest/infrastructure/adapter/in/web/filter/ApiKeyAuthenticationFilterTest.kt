package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.filter

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

private const val EXPECTED_KEY = "expected-secret-key"

class ApiKeyAuthenticationFilterTest : DescribeSpec({

    afterTest { SecurityContextHolder.clearContext() }

    describe("부팅 검증") {
        it("api-key 가 빈 문자열이면 IllegalArgumentException 으로 fail-fast") {
            shouldThrow<IllegalArgumentException> {
                ApiKeyAuthenticationFilter(expectedApiKey = "")
            }.message.shouldNotBeNull().shouldContain("INGEST_API_KEY")
        }

        it("api-key 가 공백뿐이면 fail-fast") {
            shouldThrow<IllegalArgumentException> {
                ApiKeyAuthenticationFilter(expectedApiKey = "   ")
            }
        }
    }

    describe("doFilterInternal — 인증 분기") {
        val filter = ApiKeyAuthenticationFilter(expectedApiKey = EXPECTED_KEY)

        it("정상 헤더가 들어오면 chain.doFilter 호출 + Authentication 등록") {
            val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                addHeader(ApiKeyAuthenticationFilter.HEADER_NAME, EXPECTED_KEY)
            }
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)
            // 인증 등록을 chain 호출 시점에 캡처하기 위해 doFilter 안에서 컨텍스트 검사.
            var capturedPrincipal: Any? = null
            every { chain.doFilter(any(), any()) } answers {
                capturedPrincipal = SecurityContextHolder.getContext().authentication?.principal
            }

            filter.doFilter(request, response, chain)

            verify(exactly = 1) { chain.doFilter(request, response) }
            response.status shouldBe HttpStatus.OK.value()
            capturedPrincipal shouldBe ApiKeyAuthenticationFilter.CLIENT_PRINCIPAL
            // 컨텍스트는 finally 블록에서 clear 되어야 한다 (스레드 재사용 누수 방지).
            SecurityContextHolder.getContext().authentication shouldBe null
        }

        it("헤더 누락 시 401 + JSON 응답, chain.doFilter 미호출") {
            val request = MockHttpServletRequest("POST", "/api/v1/logs")
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
            response.contentAsString.shouldContain("Unauthorized")
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }

        it("헤더 값이 빈 문자열이면 401") {
            val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                addHeader(ApiKeyAuthenticationFilter.HEADER_NAME, "")
            }
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }

        it("잘못된 헤더 값이면 401") {
            val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                addHeader(ApiKeyAuthenticationFilter.HEADER_NAME, "wrong-key")
            }
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }

        it("길이만 같은 다른 값도 401 (timing-safe 비교)") {
            val sameLengthDifferent = "X".repeat(EXPECTED_KEY.length)
            val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                addHeader(ApiKeyAuthenticationFilter.HEADER_NAME, sameLengthDifferent)
            }
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }
    }

    describe("hot-path 최적화 (#111)") {
        it("expectedKeyBytes 캐싱 — 동일 인스턴스에서 다회 호출에도 정상 인증") {
            val filter = ApiKeyAuthenticationFilter(expectedApiKey = EXPECTED_KEY)
            // expectedKeyBytes 가 init 시 1회만 인코딩되어도 매 요청 인증이 정상 동작해야 한다.
            repeat(5) {
                val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                    addHeader(ApiKeyAuthenticationFilter.HEADER_NAME, EXPECTED_KEY)
                }
                val response = MockHttpServletResponse()
                val chain = mockk<FilterChain>(relaxed = true)
                filter.doFilter(request, response, chain)
                verify(exactly = 1) { chain.doFilter(request, response) }
                response.status shouldBe HttpStatus.OK.value()
            }
        }

        it("Authentication 토큰 싱글턴 — 다회 인증에서 동일 객체 공유") {
            val filter = ApiKeyAuthenticationFilter(expectedApiKey = EXPECTED_KEY)
            val captured = mutableListOf<Any?>()

            repeat(3) {
                val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                    addHeader(ApiKeyAuthenticationFilter.HEADER_NAME, EXPECTED_KEY)
                }
                val response = MockHttpServletResponse()
                val chain = mockk<FilterChain>(relaxed = true)
                every { chain.doFilter(any(), any()) } answers {
                    captured.add(SecurityContextHolder.getContext().authentication)
                }
                filter.doFilter(request, response, chain)
            }

            // 모든 요청이 같은 토큰 인스턴스를 공유 (=== 비교)해야 한다.
            captured.size shouldBe 3
            captured[0].shouldNotBeNull()
            (captured[0] === captured[1]) shouldBe true
            (captured[1] === captured[2]) shouldBe true
        }

        it("IP 별 로그 rate-limit — 동일 IP 1초 내 2번째 호출은 false") {
            val filter = ApiKeyAuthenticationFilter(expectedApiKey = EXPECTED_KEY)
            val ip = "10.0.0.1"
            // 첫 호출 — 윈도우 진입 → true
            filter.shouldLog(ip) shouldBe true
            // 즉시 두 번째 호출 — 윈도우 내 → false
            filter.shouldLog(ip) shouldBe false
            filter.shouldLog(ip) shouldBe false
        }

        it("IP 별 로그 rate-limit — 다른 IP 는 독립 윈도우") {
            val filter = ApiKeyAuthenticationFilter(expectedApiKey = EXPECTED_KEY)
            filter.shouldLog("10.0.0.1") shouldBe true
            // 다른 IP 는 자신의 첫 윈도우이므로 true 여야 한다.
            filter.shouldLog("10.0.0.2") shouldBe true
            filter.shouldLog("10.0.0.3") shouldBe true
            // 같은 IP 재호출은 여전히 차단.
            filter.shouldLog("10.0.0.1") shouldBe false
        }
    }

    describe("shouldNotFilter — 헬스체크 우회") {
        val filter = ApiKeyAuthenticationFilter(expectedApiKey = EXPECTED_KEY)

        it("/actuator/health 는 필터 우회") {
            val request = MockHttpServletRequest("GET", "/actuator/health")
            filter.shouldNotFilter(request) shouldBe true
        }

        it("/actuator/health/liveness 는 필터 우회 (하위 경로)") {
            val request = MockHttpServletRequest("GET", "/actuator/health/liveness")
            filter.shouldNotFilter(request) shouldBe true
        }

        it("/actuator/info 는 필터 우회") {
            val request = MockHttpServletRequest("GET", "/actuator/info")
            filter.shouldNotFilter(request) shouldBe true
        }

        it("/actuator/metrics 는 필터 적용 (인증 필요)") {
            val request = MockHttpServletRequest("GET", "/actuator/metrics")
            filter.shouldNotFilter(request) shouldBe false
        }

        it("/api/v1/logs 는 필터 적용") {
            val request = MockHttpServletRequest("POST", "/api/v1/logs")
            filter.shouldNotFilter(request) shouldBe false
        }
    }
})
