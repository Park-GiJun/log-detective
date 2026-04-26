package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.filter

import com.gijun.logdetect.common.security.ApiKeyConstants
import com.gijun.logdetect.ingest.application.port.out.RateLimitCachePort
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import java.time.Duration

// 이슈 #110 — 키 길이 32자 강제 충족.
private const val EXPECTED_KEY = "expected-secret-key-with-32chars!!"
private const val ROTATION_KEY = "rotation-secret-key-with-32chars!!"
private const val SHORT_KEY = "short-key"

class ApiKeyAuthenticationFilterTest : DescribeSpec({

    afterTest { SecurityContextHolder.clearContext() }

    fun newFilter(
        apiKey: String = "",
        apiKeys: List<String> = emptyList(),
        rateLimitPort: RateLimitCachePort = noopRateLimitPort(),
        failureLimit: Int = 5,
    ) = ApiKeyAuthenticationFilter(
        expectedApiKey = apiKey,
        expectedApiKeys = apiKeys,
        rateLimitCachePort = rateLimitPort,
        meterRegistry = SimpleMeterRegistry(),
        failureLimit = failureLimit,
        failureWindow = Duration.ofMinutes(5),
        lockoutDuration = Duration.ofMinutes(5),
    )

    describe("부팅 검증") {
        it("api-key / api-keys 가 모두 비어있으면 IllegalArgumentException 으로 fail-fast") {
            shouldThrow<IllegalArgumentException> { newFilter() }
                .message.shouldNotBeNull().shouldContain("INGEST_API_KEY")
        }

        it("api-key 가 공백뿐이면 fail-fast") {
            shouldThrow<IllegalArgumentException> { newFilter(apiKey = "   ") }
        }

        it("api-key 가 32자 미만이면 fail-fast — 길이 검증 (#110 entropy)") {
            shouldThrow<IllegalArgumentException> { newFilter(apiKey = SHORT_KEY) }
                .message.shouldNotBeNull().shouldContain("최소")
        }

        it("api-keys 항목 중 하나라도 32자 미만이면 fail-fast") {
            shouldThrow<IllegalArgumentException> {
                newFilter(apiKeys = listOf(EXPECTED_KEY, SHORT_KEY))
            }
        }

        it("api-key (단일) + api-keys (다수) 둘 다 있어도 합쳐서 사용") {
            // 두 키 모두 통과해야 정상.
            val filter = newFilter(apiKey = EXPECTED_KEY, apiKeys = listOf(ROTATION_KEY))
            val response1 = MockHttpServletResponse()
            val response2 = MockHttpServletResponse()
            filter.doFilter(requestWithHeader(EXPECTED_KEY), response1, mockk(relaxed = true))
            filter.doFilter(requestWithHeader(ROTATION_KEY), response2, mockk(relaxed = true))
            response1.status shouldBe HttpStatus.OK.value()
            response2.status shouldBe HttpStatus.OK.value()
        }
    }

    describe("doFilterInternal — 인증 분기") {

        it("정상 헤더가 들어오면 chain.doFilter 호출 + Authentication 등록 + 실패 카운터 리셋") {
            val ratePort = mockk<RateLimitCachePort>(relaxed = true)
            every { ratePort.isLocked(any()) } returns false
            val filter = newFilter(apiKey = EXPECTED_KEY, rateLimitPort = ratePort)
            val request = requestWithHeader(EXPECTED_KEY)
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)
            var capturedPrincipal: Any? = null
            var capturedAuthorities: Collection<*>? = null
            every { chain.doFilter(any(), any()) } answers {
                val auth = SecurityContextHolder.getContext().authentication
                capturedPrincipal = auth?.principal
                capturedAuthorities = auth?.authorities
            }

            filter.doFilter(request, response, chain)

            verify(exactly = 1) { chain.doFilter(request, response) }
            response.status shouldBe HttpStatus.OK.value()
            // 인증 성공 시 카운터는 즉시 리셋.
            verify(exactly = 1) { ratePort.resetFailure(any()) }
            // 단일 키는 자동 부여된 clientId 가 principal — `api-client-0`.
            capturedPrincipal shouldBe "api-client-0"
            // 권한 모델 미도입 — authorities 는 비어있어야 한다 (이슈 #112 M1).
            capturedAuthorities.shouldNotBeNull().shouldBeEmpty()
            // 컨텍스트는 finally 블록에서 clear 되어야 한다 (스레드 재사용 누수 방지).
            SecurityContextHolder.getContext().authentication shouldBe null
        }

        it("clientId:key 형식이면 매칭된 clientId 가 principal 로 사용된다") {
            val filter = newFilter(apiKeys = listOf("generator:$EXPECTED_KEY"))
            val request = requestWithHeader(EXPECTED_KEY)
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)
            var capturedPrincipal: Any? = null
            every { chain.doFilter(any(), any()) } answers {
                capturedPrincipal = SecurityContextHolder.getContext().authentication?.principal
            }

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.OK.value()
            capturedPrincipal shouldBe "generator"
        }

        it("Multi-key — 두 번째 키도 동일하게 인증 통과") {
            val filter = newFilter(apiKeys = listOf("client-a:$EXPECTED_KEY", "client-b:$ROTATION_KEY"))
            val request = requestWithHeader(ROTATION_KEY)
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)
            var capturedPrincipal: Any? = null
            every { chain.doFilter(any(), any()) } answers {
                capturedPrincipal = SecurityContextHolder.getContext().authentication?.principal
            }

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.OK.value()
            capturedPrincipal shouldBe "client-b"
        }

        it("헤더 누락 시 401 + ApiErrorResponse JSON, chain.doFilter 미호출 + 실패 카운트 증가") {
            val ratePort = mockk<RateLimitCachePort>(relaxed = true)
            every { ratePort.isLocked(any()) } returns false
            every { ratePort.incrementFailure(any(), any()) } returns 1L
            val filter = newFilter(apiKey = EXPECTED_KEY, rateLimitPort = ratePort)
            val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                remoteAddr = "127.0.0.1"
            }
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            response.contentType shouldBe MediaType.APPLICATION_JSON_VALUE
            // ApiErrorResponse(error, message) 직렬화 결과 — 정찰 정보 leak 차단을 위해 단일 메시지 통일.
            response.contentAsString.shouldContain("\"error\":\"Unauthorized\"")
            response.contentAsString.shouldContain("\"message\":\"Invalid or missing API key\"")
            verify(exactly = 0) { chain.doFilter(any(), any()) }
            verify(exactly = 1) { ratePort.incrementFailure(any(), any()) }
        }

        it("헤더 값이 빈 문자열이면 401") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val request = requestWithHeader("")
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }

        it("잘못된 헤더 값이면 401") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val request = requestWithHeader("wrong-key-padding-padding-padding!!")
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }

        it("길이만 같은 다른 값도 401 (length-safe SHA-256 비교)") {
            val sameLengthDifferent = "X".repeat(EXPECTED_KEY.length)
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val request = requestWithHeader(sameLengthDifferent)
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }

        it("길이가 짧은 값도 401 (length leak 차단 — SHA-256 정규화 후 비교)") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val request = requestWithHeader("X")
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
        }

        // 이슈 #108 — whitespace-only 헤더가 isNullOrBlank() 분기로 401 처리되는지 확인.
        // 회귀 시나리오: trim 누락으로 빈 키와 공백 키가 다르게 취급되면 인증 우회 위험.
        it("헤더 값이 단일 공백 문자면 401") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val request = requestWithHeader(" ")
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }

        it("헤더 값이 탭 문자뿐이면 401") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val request = requestWithHeader("\t")
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }

        // 이슈 #108 — Servlet `getHeader` 는 동일 이름 헤더가 여럿이면 첫 값을 반환한다.
        // 첫 값이 정상 키면 통과, 첫 값이 잘못된 키면 두 번째에 정답이 있어도 401.
        it("동일 헤더 다중 값 — 첫 값이 정상 키면 통과 (Servlet getHeader 의 first-value 동작)") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                addHeader(ApiKeyConstants.HEADER_NAME, EXPECTED_KEY)
                addHeader(ApiKeyConstants.HEADER_NAME, "second-wrong")
                remoteAddr = "127.0.0.1"
            }
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.OK.value()
            verify(exactly = 1) { chain.doFilter(request, response) }
        }

        it("동일 헤더 다중 값 — 첫 값이 잘못된 키면 401 (두 번째에 정답이 있어도)") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                addHeader(ApiKeyConstants.HEADER_NAME, "first-wrong-padding-padding-padding!!")
                addHeader(ApiKeyConstants.HEADER_NAME, EXPECTED_KEY)
                remoteAddr = "127.0.0.1"
            }
            val response = MockHttpServletResponse()
            val chain = mockk<FilterChain>(relaxed = true)

            filter.doFilter(request, response, chain)

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { chain.doFilter(any(), any()) }
        }
    }

    describe("Rate-limit + lockout (#110)") {

        it("실패 5회 누적 시 lock 호출 + 429 응답") {
            val ratePort = mockk<RateLimitCachePort>(relaxed = true)
            every { ratePort.isLocked(any()) } returns false
            every { ratePort.incrementFailure(any(), any()) } returns 5L
            val filter = newFilter(apiKey = EXPECTED_KEY, rateLimitPort = ratePort, failureLimit = 5)

            val request = requestWithHeader("wrong-key-padding-padding-padding!!")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, mockk<FilterChain>(relaxed = true))

            response.status shouldBe HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentAsString.shouldContain("Too Many Requests")
            response.contentAsString.shouldContain("retryAfter")
            response.getHeader("Retry-After").shouldNotBeNull()
            verify(exactly = 1) { ratePort.lock(any(), any()) }
        }

        it("lock 상태 IP 는 키 검증 없이 즉시 429") {
            val ratePort = mockk<RateLimitCachePort>(relaxed = true)
            every { ratePort.isLocked(any()) } returns true
            every { ratePort.lockTtlSeconds(any()) } returns 250L
            val filter = newFilter(apiKey = EXPECTED_KEY, rateLimitPort = ratePort)

            // 정상 키를 줘도 락아웃 중이면 차단.
            val request = requestWithHeader(EXPECTED_KEY)
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, mockk<FilterChain>(relaxed = true))

            response.status shouldBe HttpStatus.TOO_MANY_REQUESTS.value()
            response.getHeader("Retry-After") shouldBe "250"
            // 락아웃 상태에서는 incrementFailure 호출 안 됨 (이미 차단 중이라 카운트 무의미).
            verify(exactly = 0) { ratePort.incrementFailure(any(), any()) }
        }

        it("실패 횟수 < limit 이면 401 응답 (lock 미호출)") {
            val ratePort = mockk<RateLimitCachePort>(relaxed = true)
            every { ratePort.isLocked(any()) } returns false
            every { ratePort.incrementFailure(any(), any()) } returns 3L
            val filter = newFilter(apiKey = EXPECTED_KEY, rateLimitPort = ratePort, failureLimit = 5)

            val request = requestWithHeader("wrong-key-padding-padding-padding!!")
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, mockk<FilterChain>(relaxed = true))

            response.status shouldBe HttpStatus.UNAUTHORIZED.value()
            verify(exactly = 0) { ratePort.lock(any(), any()) }
        }
    }

    describe("X-Forwarded-For 처리 (#110)") {

        it("X-Forwarded-For 의 첫 IP 가 클라이언트 키로 사용된다") {
            val ratePort = mockk<RateLimitCachePort>(relaxed = true)
            every { ratePort.isLocked("203.0.113.1") } returns false
            every { ratePort.incrementFailure("203.0.113.1", any()) } returns 1L
            val filter = newFilter(apiKey = EXPECTED_KEY, rateLimitPort = ratePort)

            val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.1, 10.0.0.2")
                remoteAddr = "127.0.0.1"
            }
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, mockk<FilterChain>(relaxed = true))

            // X-Forwarded-For 의 첫 IP 가 키로 전달되었는지 검증.
            verify { ratePort.incrementFailure("203.0.113.1", any()) }
            verify(exactly = 0) { ratePort.incrementFailure("127.0.0.1", any()) }
        }

        it("X-Forwarded-For 가 없으면 remoteAddr fallback") {
            val ratePort = mockk<RateLimitCachePort>(relaxed = true)
            every { ratePort.isLocked("127.0.0.1") } returns false
            every { ratePort.incrementFailure("127.0.0.1", any()) } returns 1L
            val filter = newFilter(apiKey = EXPECTED_KEY, rateLimitPort = ratePort)

            val request = MockHttpServletRequest("POST", "/api/v1/logs").apply {
                remoteAddr = "127.0.0.1"
            }
            val response = MockHttpServletResponse()

            filter.doFilter(request, response, mockk<FilterChain>(relaxed = true))

            verify { ratePort.incrementFailure("127.0.0.1", any()) }
        }
    }

    describe("PERMIT_ALL_PATHS — Filter / SecurityConfig 공유 상수") {
        it("actuator/health, info, prometheus 만 포함 (의도치 않은 추가 차단)") {
            ApiKeyAuthenticationFilter.PERMIT_ALL_PATHS shouldBe listOf(
                "/actuator/health",
                "/actuator/info",
                "/actuator/prometheus",
            )
        }

        it("isPermitAllPath — 정확히 매칭되는 경로는 true") {
            ApiKeyAuthenticationFilter.isPermitAllPath("/actuator/health") shouldBe true
            ApiKeyAuthenticationFilter.isPermitAllPath("/actuator/info") shouldBe true
            ApiKeyAuthenticationFilter.isPermitAllPath("/actuator/prometheus") shouldBe true
        }

        it("isPermitAllPath — 하위 경로(startsWith) 도 true") {
            ApiKeyAuthenticationFilter.isPermitAllPath("/actuator/health/liveness") shouldBe true
            ApiKeyAuthenticationFilter.isPermitAllPath("/actuator/health/readiness") shouldBe true
        }

        it("isPermitAllPath — 다른 actuator 경로는 false") {
            ApiKeyAuthenticationFilter.isPermitAllPath("/actuator/metrics") shouldBe false
            ApiKeyAuthenticationFilter.isPermitAllPath("/actuator/env") shouldBe false
        }

        it("isPermitAllPath — 비즈니스 API 는 false") {
            ApiKeyAuthenticationFilter.isPermitAllPath("/api/v1/logs") shouldBe false
        }
    }

    describe("hot-path 최적화 (#111 / #115)") {
        it("키 digest 캐싱 — 동일 인스턴스에서 다회 호출에도 정상 인증") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            // SHA-256 digest 가 init 시 1회만 계산되어도 매 요청 인증이 정상 동작해야 한다.
            repeat(5) {
                val request = requestWithHeader(EXPECTED_KEY)
                val response = MockHttpServletResponse()
                val chain = mockk<FilterChain>(relaxed = true)
                filter.doFilter(request, response, chain)
                verify(exactly = 1) { chain.doFilter(request, response) }
                response.status shouldBe HttpStatus.OK.value()
            }
        }

        it("Authentication 토큰 캐싱 — 동일 clientId 에 대해 다회 인증에서 동일 객체 공유") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val captured = mutableListOf<Any?>()

            repeat(3) {
                val request = requestWithHeader(EXPECTED_KEY)
                val response = MockHttpServletResponse()
                val chain = mockk<FilterChain>(relaxed = true)
                every { chain.doFilter(any(), any()) } answers {
                    captured.add(SecurityContextHolder.getContext().authentication)
                }
                filter.doFilter(request, response, chain)
            }

            // 모든 요청이 같은 토큰 인스턴스를 공유 (=== 비교)해야 한다 (clientId 별 캐싱).
            captured.size shouldBe 3
            captured[0].shouldNotBeNull()
            (captured[0] === captured[1]) shouldBe true
            (captured[1] === captured[2]) shouldBe true
        }

        it("IP 별 로그 rate-limit — 동일 IP 1초 내 2번째 호출은 false") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            val ip = "10.0.0.1"
            // 첫 호출 — 윈도우 진입 → true
            filter.shouldLog(ip) shouldBe true
            // 즉시 두 번째 호출 — 윈도우 내 → false
            filter.shouldLog(ip) shouldBe false
            filter.shouldLog(ip) shouldBe false
        }

        it("IP 별 로그 rate-limit — 다른 IP 는 독립 윈도우") {
            val filter = newFilter(apiKey = EXPECTED_KEY)
            filter.shouldLog("10.0.0.1") shouldBe true
            // 다른 IP 는 자신의 첫 윈도우이므로 true 여야 한다.
            filter.shouldLog("10.0.0.2") shouldBe true
            filter.shouldLog("10.0.0.3") shouldBe true
            // 같은 IP 재호출은 여전히 차단.
            filter.shouldLog("10.0.0.1") shouldBe false
        }
    }

    describe("shouldNotFilter — 헬스체크 우회") {
        val filter = newFilter(apiKey = EXPECTED_KEY)

        it("PERMIT_ALL_PATHS 의 모든 경로는 우회") {
            ApiKeyAuthenticationFilter.PERMIT_ALL_PATHS.forEach { path ->
                val request = MockHttpServletRequest("GET", path)
                filter.shouldNotFilter(request) shouldBe true
            }
        }

        it("/actuator/health/liveness 는 필터 우회 (하위 경로)") {
            filter.shouldNotFilter(MockHttpServletRequest("GET", "/actuator/health/liveness")) shouldBe true
        }

        it("/actuator/info 는 필터 우회") {
            filter.shouldNotFilter(MockHttpServletRequest("GET", "/actuator/info")) shouldBe true
        }

        it("/actuator/prometheus 는 필터 우회 (Prometheus scraper)") {
            filter.shouldNotFilter(MockHttpServletRequest("GET", "/actuator/prometheus")) shouldBe true
        }

        it("/actuator/metrics 는 필터 적용 (인증 필요)") {
            filter.shouldNotFilter(MockHttpServletRequest("GET", "/actuator/metrics")) shouldBe false
        }

        it("/api/v1/logs 는 필터 적용") {
            filter.shouldNotFilter(MockHttpServletRequest("POST", "/api/v1/logs")) shouldBe false
        }
    }
})

private fun requestWithHeader(value: String): MockHttpServletRequest =
    MockHttpServletRequest("POST", "/api/v1/logs").apply {
        addHeader(ApiKeyConstants.HEADER_NAME, value)
        remoteAddr = "127.0.0.1"
    }

private fun noopRateLimitPort(): RateLimitCachePort = object : RateLimitCachePort {
    override fun incrementFailure(key: String, ttl: Duration): Long = 1L
    override fun resetFailure(key: String) = Unit
    override fun lock(key: String, ttl: Duration) = Unit
    override fun isLocked(key: String): Boolean = false
    override fun lockTtlSeconds(key: String): Long = 0L
}
