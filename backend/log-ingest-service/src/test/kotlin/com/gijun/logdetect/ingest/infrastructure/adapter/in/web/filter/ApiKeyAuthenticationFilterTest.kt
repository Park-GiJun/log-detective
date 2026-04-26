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
