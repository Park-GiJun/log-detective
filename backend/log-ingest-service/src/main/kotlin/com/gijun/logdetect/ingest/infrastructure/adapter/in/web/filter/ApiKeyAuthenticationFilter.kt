package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.filter

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.time.Duration

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
 *
 * Hot-path 최적화 (#111):
 *  - expectedKeyBytes 를 init 시 1회만 인코딩 (요청마다 toByteArray 호출 회피)
 *  - 인증 성공 토큰을 immutable 싱글턴으로 재사용 (요청마다 객체 할당 회피)
 *  - 인증 실패 로그를 IP 별 1초 1회로 rate-limit (악성 IP 의 로그 폭주 차단)
 *    → Caffeine maximumSize + expireAfterWrite 로 메모리 무한 증가 방지
 */
@Component
class ApiKeyAuthenticationFilter(
    @Value("\${logdetect.ingest.api-key:}") private val expectedApiKey: String,
) : OncePerRequestFilter() {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    // 요청당 toByteArray 호출 회피 — expected 값은 immutable 이므로 init 시 1회만 인코딩.
    private val expectedKeyBytes: ByteArray = expectedApiKey.toByteArray(Charsets.UTF_8)

    // IP 별 마지막 로그 시각 (epoch millis) — Caffeine 으로 캡 + TTL 부여.
    // 메모리 폭증 방지: 동시 공격 IP 가 수만 개여도 maximumSize 까지만 유지.
    private val ipLastLogged: Cache<String, Long> = Caffeine.newBuilder()
        .maximumSize(LOG_RATE_LIMIT_MAX_IPS)
        .expireAfterWrite(Duration.ofMinutes(LOG_RATE_LIMIT_TTL_MINUTES))
        .build()

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
        if (provided.isNullOrBlank() || !verifyKey(provided)) {
            // 인증 실패 로그는 path / remoteAddr 만 기록 (헤더 값은 누설 위험으로 마스킹 생략 = 미기록).
            // IP 별 1초 1회로 rate-limit — 악성 IP 가 디스크/Loki 채우는 것 방지.
            val remoteAddr = request.remoteAddr ?: UNKNOWN_REMOTE
            if (shouldLog(remoteAddr)) {
                logger.warn(
                    "API Key 인증 실패 — path: {}, remoteAddr: {}",
                    request.requestURI,
                    remoteAddr,
                )
            }
            response.status = HttpStatus.UNAUTHORIZED.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.characterEncoding = Charsets.UTF_8.name()
            response.writer.write(UNAUTHORIZED_BODY)
            return
        }

        // 인증 성공 — Authentication 등록. authenticated() 룰을 통과시키기 위함.
        // SecurityContextHolder 의 context 자체는 thread-local 이므로 immutable 토큰 객체 공유 안전.
        SecurityContextHolder.getContext().authentication = AUTHENTICATED_TOKEN
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

    private fun verifyKey(provided: String): Boolean {
        // 요청마다 expected 의 byte 인코딩을 새로 할 필요 없음 — init 캐시 사용.
        // 길이가 달라도 동일 시간으로 비교하기 위해 byte 배열로 비교.
        val providedBytes = provided.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(providedBytes, expectedKeyBytes)
    }

    /**
     * 인증 실패 로그 rate-limit — IP 별 [LOG_RATE_LIMIT_WINDOW_MS] 당 1회.
     *
     * 반환 true: 이번 호출에서 로깅하라.
     * 반환 false: 같은 IP 가 윈도우 내 이미 로깅됨 — 스킵.
     */
    internal fun shouldLog(ip: String): Boolean {
        val now = System.currentTimeMillis()
        val last = ipLastLogged.getIfPresent(ip) ?: 0L
        return if (now - last > LOG_RATE_LIMIT_WINDOW_MS) {
            ipLastLogged.put(ip, now)
            true
        } else {
            false
        }
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

        private const val UNKNOWN_REMOTE = "unknown"

        // 로그 rate-limit 윈도우 (1초) — 동일 IP 폭주 방지하면서도 추적성은 유지.
        private const val LOG_RATE_LIMIT_WINDOW_MS = 1_000L

        // Caffeine 캐시 상한 — 동시 공격 IP 수만 개여도 메모리 < 수 MB 로 묶음.
        private const val LOG_RATE_LIMIT_MAX_IPS = 10_000L
        private const val LOG_RATE_LIMIT_TTL_MINUTES = 5L

        /**
         * 인증 성공 시 등록할 Authentication 토큰 — immutable 싱글턴.
         *
         * UsernamePasswordAuthenticationToken 은 principal/credentials/authorities 를
         * 생성자에서만 받는 사실상 immutable 객체이며, isAuthenticated() = true 로 고정된다.
         * SecurityContextHolder 자체가 thread-local 컨텍스트라 토큰 공유는 race 없음.
         * 요청마다 객체/리스트 할당을 제거해 hot-path GC 압력을 낮춘다.
         */
        private val AUTHENTICATED_TOKEN: Authentication = UsernamePasswordAuthenticationToken(
            CLIENT_PRINCIPAL,
            null,
            listOf(SimpleGrantedAuthority(ROLE_INGEST)),
        )
    }
}
