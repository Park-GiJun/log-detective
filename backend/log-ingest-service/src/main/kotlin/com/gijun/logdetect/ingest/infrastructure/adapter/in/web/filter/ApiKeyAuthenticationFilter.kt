package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * X-API-Key 헤더 기반 단순 인증 필터.
 *
 * WHY — log-ingest-service 는 internal 서비스 (Generator → Ingest) 간 호출만 받기에
 * JWT/OAuth 인프라 없이 환경변수로 주입한 사전 공유키만으로 충분하다.
 * MessageDigest.isEqual 로 timing-safe 비교를 수행해 측면 채널 추론을 차단한다.
 *
 * 운영 — `INGEST_API_KEY` 환경변수가 비어있으면 부팅 시 즉시 fail-fast 한다.
 * permitAll 회귀(이슈 #86) 가 다시 들어와도 본 필터가 실행되지 않으면 보안 결함이 즉시 드러나도록
 * SecurityFilterChain 의 필수 단계로 선언한다.
 */
@Component
class ApiKeyAuthenticationFilter(
    @Value("\${logdetect.ingest.api-key:}") private val expectedApiKey: String,
) : OncePerRequestFilter() {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    init {
        require(expectedApiKey.isNotBlank()) {
            "logdetect.ingest.api-key 가 비어있음. INGEST_API_KEY 환경변수를 반드시 설정하라."
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        // actuator/health, info 등 permitAll 경로는 SecurityFilterChain 단계에서 분기되지만
        // OncePerRequestFilter 는 모든 요청을 통과하므로 shouldNotFilter 로 보조 분기.
        val provided = request.getHeader(HEADER_NAME)
        if (provided.isNullOrBlank() || !timingSafeEquals(provided, expectedApiKey)) {
            // 인증 실패 로그는 path / remoteAddr 만 기록 (헤더 값은 누설 위험으로 마스킹 생략 = 미기록).
            logger.warn(
                "API Key 인증 실패 — path: {}, remoteAddr: {}",
                request.requestURI,
                request.remoteAddr,
            )
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(UNAUTHORIZED_BODY)
            return
        }

        // 인증 성공 — Authentication 등록. authenticated() 룰을 통과시키기 위함.
        val authentication = UsernamePasswordAuthenticationToken(
            CLIENT_PRINCIPAL,
            null,
            listOf(SimpleGrantedAuthority(ROLE_INGEST)),
        )
        SecurityContextHolder.getContext().authentication = authentication
        try {
            chain.doFilter(request, response)
        } finally {
            // 다음 스레드 재사용 시 인증 컨텍스트 누수 방지.
            SecurityContextHolder.clearContext()
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        // SecurityFilterChain permitAll 경로와 동기화 — 헬스체크는 인증 우회.
        // 경로 정의를 PERMIT_ALL_PATHS 단일 상수로 모아 SecurityConfig 와의 표류(drift) 차단.
        val path = request.requestURI ?: return false
        return isPermitAllPath(path)
    }

    private fun timingSafeEquals(provided: String, expected: String): Boolean {
        // 길이가 달라도 동일 시간으로 비교하기 위해 byte 배열로 비교한다.
        val a = provided.toByteArray(Charsets.UTF_8)
        val b = expected.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(a, b)
    }

    companion object {
        const val HEADER_NAME = "X-API-Key"
        const val CLIENT_PRINCIPAL = "api-client"
        const val ROLE_INGEST = "ROLE_INGEST"

        /**
         * 인증 우회 경로 — Filter / SecurityConfig 가 동일 소스를 참조해 표류 방지.
         * 운영 LB 헬스체크 + 디버그용 info 만 허용. 본문은 민감 정보 미포함이라 인증 면제 가능.
         */
        val PERMIT_ALL_PATHS: List<String> = listOf(
            "/actuator/health",
            "/actuator/info",
        )

        /**
         * 경로가 PERMIT_ALL_PATHS 중 하나로 시작하면 true.
         * `/actuator/health/liveness` 같은 하위 경로도 허용 — startsWith 매칭.
         */
        fun isPermitAllPath(path: String): Boolean = PERMIT_ALL_PATHS.any { path.startsWith(it) }

        private const val UNAUTHORIZED_BODY =
            """{"error":"Unauthorized","message":"Invalid or missing API key"}"""
    }
}
