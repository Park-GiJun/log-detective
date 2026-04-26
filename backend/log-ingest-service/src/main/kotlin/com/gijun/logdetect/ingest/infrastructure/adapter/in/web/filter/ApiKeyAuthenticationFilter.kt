package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.filter

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.gijun.logdetect.common.security.ApiKeyConstants
import com.gijun.logdetect.ingest.application.port.out.RateLimitCachePort
import io.micrometer.core.instrument.MeterRegistry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest
import java.time.Duration

/**
 * X-API-Key 헤더 기반 인증 필터.
 *
 * WHY — log-ingest-service 는 internal 서비스 (Generator → Ingest) 호출만 받으므로
 * JWT/OAuth 인프라 없이 사전 공유키만으로 충분하다.
 *
 * 이슈 #110 보강 항목:
 *  1. Length-safe 비교 — 길이가 달라도 SHA-256 정규화 후 isEqual 로 길이 leak 차단.
 *  2. Multi-key — 키 회전을 위해 여러 키를 동시에 허용 (OR).
 *  3. Brute-force 방어 — IP 단위 실패 카운터 + 락아웃 (RateLimitCachePort 위임, Redis 분산).
 *  4. 응답 통일 — 누락/오류 모두 401 [ApiErrorResponse], 락아웃은 429 + Retry-After.
 *  5. X-Forwarded-For — proxy 환경에서 실제 클라이언트 IP 추출.
 *  6. 길이 검증 — 부팅 시 키 최소 32 자 요구 (엔트로피 확보, fail-fast).
 *  7. 메트릭 — `auth.failure.total{path,reason}` 으로 침해 시도 가시화.
 *  8. 클라이언트 식별 — 매칭된 키의 clientId 를 principal 로 사용.
 *
 * 운영 — 어떤 키도 설정되지 않았으면 부팅 시 즉시 fail-fast (permitAll 회귀 #86 재발 방지).
 *
 * Hot-path 최적화 (#111 / #115):
 *  - SHA-256 digest 를 init 시 1회만 계산 (요청마다 인코딩 회피)
 *  - clientId 별 [Authentication] 토큰을 init 시 미리 생성해 immutable 싱글턴처럼 재사용
 *    → 요청마다 객체/리스트 할당 제거, GC 압력 완화
 *  - 인증 실패 로그를 IP 별 1초 1회로 rate-limit (악성 IP 의 로그 폭주 차단)
 *    → Caffeine maximumSize + expireAfterWrite 로 메모리 무한 증가 방지
 *
 * Brute-force 방어 (#110) 와 로그 rate-limit (#111) 의 역할 구분:
 *  - Caffeine ipLastLogged: **로그 spam 방지** (1초 1회 — 디스크/Loki 보호)
 *  - RateLimitCachePort: **보안 lockout** (5회/5분 — 401 → 429 전환, 분산 카운터)
 */
class ApiKeyAuthenticationFilter(
    expectedApiKey: String,
    expectedApiKeys: List<String>,
    private val rateLimitCachePort: RateLimitCachePort,
    private val meterRegistry: MeterRegistry,
    private val objectMapper: ObjectMapper = ObjectMapper(),
    private val failureLimit: Int = DEFAULT_FAILURE_LIMIT,
    private val failureWindow: Duration = DEFAULT_FAILURE_WINDOW,
    private val lockoutDuration: Duration = DEFAULT_LOCKOUT_DURATION,
) : OncePerRequestFilter() {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    /**
     * clientId → SHA-256 digest 매핑. 부팅 시 단 한 번 계산해 hot-path 비용을 제거.
     * Multi-key + clientId 매칭 + length-safe 비교를 모두 동일 자료구조로 해결.
     */
    private val keyDigests: Map<String, ByteArray>

    /**
     * clientId → [Authentication] 토큰 매핑 (immutable). 요청마다 객체 할당을 제거.
     *
     * UsernamePasswordAuthenticationToken 은 사실상 immutable 이며 isAuthenticated()=true 로 고정.
     * SecurityContextHolder 자체가 thread-local 컨텍스트라 토큰 공유는 race 없음.
     * authorities 는 비워둔다 — 현재 권한 모델(role 분기) 미사용. 도입 시 추가 (M1).
     */
    private val clientTokens: Map<String, Authentication>

    // IP 별 마지막 로그 시각 (epoch millis) — Caffeine 으로 캡 + TTL 부여.
    // 메모리 폭증 방지: 동시 공격 IP 가 수만 개여도 maximumSize 까지만 유지.
    private val ipLastLogged: Cache<String, Long> = Caffeine.newBuilder()
        .maximumSize(LOG_RATE_LIMIT_MAX_IPS)
        .expireAfterWrite(Duration.ofMinutes(LOG_RATE_LIMIT_TTL_MINUTES))
        .build()

    init {
        // 단일 + 리스트 두 형식 모두 수용 (backward compat). 합집합으로 처리.
        val raw = buildList {
            if (expectedApiKey.isNotBlank()) add(expectedApiKey.trim())
            addAll(expectedApiKeys.map(String::trim).filter(String::isNotBlank))
        }
        require(raw.isNotEmpty()) {
            "logdetect.ingest.api-key 또는 logdetect.ingest.api-keys 가 비어있음. " +
                "INGEST_API_KEY/INGEST_API_KEYS 환경변수를 반드시 설정하라."
        }

        // "clientId:key" / "key" 두 형식 파싱. 길이 검증은 key 부분에 한정.
        val parsed = raw.mapIndexed { idx, entry ->
            val (id, key) = parseEntry(entry, idx)
            require(key.length >= MIN_KEY_LENGTH) {
                "INGEST_API_KEY 는 최소 $MIN_KEY_LENGTH 자 이상이어야 함 (clientId=$id)."
            }
            id to sha256(key)
        }
        // 동일 clientId 가 여러 번 나올 경우 마지막 키만 유효하도록 toMap 정의 — 회전 중간 단계 허용.
        keyDigests = parsed.toMap()
        // 각 clientId 별 인증 토큰 미리 생성 — 요청마다 객체 할당 회피 (#111 hot-path).
        clientTokens = keyDigests.keys.associateWith { clientId ->
            UsernamePasswordAuthenticationToken(clientId, null, emptyList())
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val clientIp = resolveClientIp(request)
        val path = request.requestURI ?: "/"

        // 락아웃 진입 확인 — 이미 차단 중이면 401 도 아닌 429 로 응답해 무차별 시도 비용 상승.
        if (rateLimitCachePort.isLocked(clientIp)) {
            val retryAfter = rateLimitCachePort.lockTtlSeconds(clientIp)
            recordFailure(path, REASON_LOCKED)
            writeLocked(response, retryAfter)
            return
        }

        val provided = request.getHeader(ApiKeyConstants.HEADER_NAME)
        val matched = if (provided.isNullOrBlank()) null else verifyKey(provided)

        if (matched == null) {
            handleAuthFailure(request, response, clientIp, path, provided.isNullOrBlank())
            return
        }

        // 인증 성공 — 실패 카운터 리셋, 사전 생성한 immutable 토큰을 SecurityContext 에 등록.
        rateLimitCachePort.resetFailure(clientIp)
        // 매칭된 clientId 의 사전 생성 토큰을 재사용 — 요청마다 새 객체 할당하지 않음 (#111).
        SecurityContextHolder.getContext().authentication = clientTokens.getValue(matched)
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

    /**
     * provided 키와 모든 등록 키를 length-safe 로 비교하고 매칭된 clientId 를 반환.
     * 모든 키가 동일 시간 (SHA-256 digest 32 byte) 으로 비교되어 길이 leak 차단.
     */
    private fun verifyKey(provided: String): String? {
        val providedDigest = sha256(provided)
        // 모든 키를 순회하더라도 byte 비교 자체는 동일 시간이며, 실패 시 carry over 비용은 무시 가능.
        var matchedClient: String? = null
        for ((clientId, expectedDigest) in keyDigests) {
            if (MessageDigest.isEqual(providedDigest, expectedDigest)) {
                // 첫 매칭을 잡아두되 break 하지 않고 끝까지 순회 → 시간 차 leak 방지.
                if (matchedClient == null) matchedClient = clientId
            }
        }
        return matchedClient
    }

    private fun handleAuthFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        clientIp: String,
        path: String,
        missing: Boolean,
    ) {
        // brute-force 방어 — 카운터 증가 후 임계 도달 시 락아웃 진입.
        val attempts = rateLimitCachePort.incrementFailure(clientIp, failureWindow)
        val reason = if (missing) REASON_MISSING else REASON_INVALID
        recordFailure(path, reason)

        if (attempts >= failureLimit) {
            rateLimitCachePort.lock(clientIp, lockoutDuration)
            recordFailure(path, REASON_LOCKED)
            // 락아웃 직후 1회는 안내성 429 로 즉시 응답.
            // 보안 lockout 은 빈도가 낮고 추적성이 중요 — 로그 rate-limit 적용 안 함.
            logger.warn(
                "API Key brute-force 의심 — ip: {}, path: {}, attempts: {}",
                clientIp,
                path,
                attempts,
            )
            writeLocked(response, lockoutDuration.toSeconds())
            return
        }

        // 인증 실패 로그 — 헤더 값은 누설 위험으로 절대 미기록.
        // IP 별 1초 1회로 rate-limit — 악성 IP 가 디스크/Loki 채우는 것 방지 (#111).
        if (shouldLog(clientIp)) {
            logger.warn(
                "API Key 인증 실패 — path: {}, ip: {}, reason: {}, attempts: {}",
                path,
                clientIp,
                reason,
                attempts,
            )
        }
        writeUnauthorized(response)
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

    /**
     * 401 응답 본문 작성 — [ApiErrorResponse] 를 ObjectMapper 로 직렬화.
     *
     * WHY — raw 문자열 리터럴 대신 type-safe DTO + ObjectMapper 로 두면
     * 필드 추가/변경 시 컴파일러 안전성 + JSON escaping 책임 분리.
     * 누락/잘못된 키 구분은 응답 본문에 포함하지 않는다 — 정찰 정보 leak 차단.
     */
    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        response.writer.write(objectMapper.writeValueAsString(UNAUTHORIZED_BODY))
    }

    private fun writeLocked(response: HttpServletResponse, retryAfterSeconds: Long) {
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = Charsets.UTF_8.name()
        // RFC7231 Retry-After — 운영 LB / client 가 자동 backoff 가능하도록 명시.
        response.setHeader("Retry-After", retryAfterSeconds.toString())
        response.writer.write(
            objectMapper.writeValueAsString(
                LockedResponse(
                    error = "Too Many Requests",
                    retryAfter = retryAfterSeconds,
                ),
            ),
        )
    }

    private fun recordFailure(path: String, reason: String) {
        meterRegistry.counter(METRIC_AUTH_FAILURE, "path", path, "reason", reason).increment()
    }

    /**
     * X-Forwarded-For 우선, 없으면 remoteAddr fallback.
     * 첫 번째 IP 만 신뢰 (proxy chain 의 가장 client 측). 운영 시 LB 가 X-Forwarded-For 를
     * 정확히 세팅한다는 전제하에 사용.
     */
    private fun resolveClientIp(request: HttpServletRequest): String =
        request.getHeader(HEADER_X_FORWARDED_FOR)
            ?.split(",")
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: (request.remoteAddr ?: UNKNOWN_REMOTE)

    private fun parseEntry(entry: String, idx: Int): Pair<String, String> {
        // "clientId:key" 형식 우선 — 구분자 첫 번째 ':' 만 split (key 안에 ':' 있어도 안전).
        val colon = entry.indexOf(':')
        return if (colon > 0 && colon < entry.length - 1) {
            val id = entry.substring(0, colon).trim()
            val key = entry.substring(colon + 1).trim()
            (if (id.isNotBlank()) id else "$DEFAULT_CLIENT_PRINCIPAL-$idx") to key
        } else {
            // 단일 키 — clientId 자동 부여.
            "$DEFAULT_CLIENT_PRINCIPAL-$idx" to entry
        }
    }

    private fun sha256(s: String): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))

    /**
     * 429 응답 본문 — Jackson 직렬화용 내부 DTO.
     */
    private data class LockedResponse(
        val error: String,
        val retryAfter: Long,
    )

    companion object {
        /**
         * 헤더명은 [ApiKeyConstants.HEADER_NAME] 단일 source 사용.
         * 본 상수는 기존 테스트/통합 테스트 호환을 위한 alias 로 보존한다.
         */
        @Deprecated(
            message = "Use ApiKeyConstants.HEADER_NAME — log-common 의 단일 source.",
            replaceWith = ReplaceWith(
                "ApiKeyConstants.HEADER_NAME",
                "com.gijun.logdetect.common.security.ApiKeyConstants",
            ),
        )
        const val HEADER_NAME = ApiKeyConstants.HEADER_NAME
        const val HEADER_X_FORWARDED_FOR = "X-Forwarded-For"
        const val DEFAULT_CLIENT_PRINCIPAL = "api-client"

        /** @deprecated 호환 alias — 외부에서 [DEFAULT_CLIENT_PRINCIPAL] 사용 권장. */
        @Deprecated(
            message = "Use DEFAULT_CLIENT_PRINCIPAL.",
            replaceWith = ReplaceWith("DEFAULT_CLIENT_PRINCIPAL"),
        )
        const val CLIENT_PRINCIPAL = DEFAULT_CLIENT_PRINCIPAL

        const val MIN_KEY_LENGTH = 32

        const val METRIC_AUTH_FAILURE = "auth.failure.total"
        const val REASON_MISSING = "missing"
        const val REASON_INVALID = "invalid"
        const val REASON_LOCKED = "locked"

        val DEFAULT_FAILURE_WINDOW: Duration = Duration.ofMinutes(5)
        val DEFAULT_LOCKOUT_DURATION: Duration = Duration.ofMinutes(5)
        const val DEFAULT_FAILURE_LIMIT = 5

        /**
         * 인증 우회 경로 — Filter / SecurityConfig 가 동일 소스를 참조해 표류 방지.
         * 운영 LB 헬스체크 + 디버그용 info + Prometheus scrape 만 허용.
         * 본문은 민감 정보 미포함이라 인증 면제 가능.
         */
        val PERMIT_ALL_PATHS: List<String> = listOf(
            "/actuator/health",
            "/actuator/info",
            "/actuator/prometheus",
        )

        /**
         * 경로가 PERMIT_ALL_PATHS 중 하나로 시작하면 true.
         * `/actuator/health/liveness` 같은 하위 경로도 허용 — startsWith 매칭.
         */
        fun isPermitAllPath(path: String): Boolean = PERMIT_ALL_PATHS.any { path.startsWith(it) }

        // 응답은 누락/오류 구분 없이 단일 메시지 — 정찰 단계의 정보 leak 차단.
        private val UNAUTHORIZED_BODY = ApiErrorResponse(
            error = "Unauthorized",
            message = "Invalid or missing API key",
        )

        private const val UNKNOWN_REMOTE = "unknown"

        // 로그 rate-limit 윈도우 (1초) — 동일 IP 폭주 방지하면서도 추적성은 유지.
        private const val LOG_RATE_LIMIT_WINDOW_MS = 1_000L

        // Caffeine 캐시 상한 — 동시 공격 IP 수만 개여도 메모리 < 수 MB 로 묶음.
        private const val LOG_RATE_LIMIT_MAX_IPS = 10_000L
        private const val LOG_RATE_LIMIT_TTL_MINUTES = 5L
    }
}
