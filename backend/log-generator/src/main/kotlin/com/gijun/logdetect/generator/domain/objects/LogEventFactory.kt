package com.gijun.logdetect.generator.domain.objects

import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.model.LogEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.random.Random

object LogEventFactory {

    private val normalUserIds: List<Long> = (1L..NORMAL_USER_COUNT).toList()

    private val adminUserIds: List<Long> = (ADMIN_USER_ID_START..ADMIN_USER_ID_END).toList()

    private val sources = listOf(
        "auth-service",
        "api-gateway",
        "order-service",
        "payment-service",
        "admin-portal",
    )

    private const val RARE_SOURCE = "legacy-batch"

    private val normalEndpoints = listOf(
        "/api/users/me", "/api/orders", "/api/products/search",
        "/api/payments/history", "/api/auth/refresh",
    )

    private val sqlInjectionPayloads = listOf(
        "' OR 1=1 --",
        "' UNION SELECT username, password FROM users --",
        "'; DROP TABLE sessions; --",
        "admin' --",
        "1' OR '1'='1",
    )

    // ─────────────────────────────── 정상 로그 ───────────────────────────────

    fun createNormal(): LogEvent {
        val source = sources.random()
        val level = randomNormalLevel()
        val userId = normalUserIds.random()
        val endpoint = normalEndpoints.random()
        val statusCode = if (level == "ERROR") "500" else if (level == "WARN") "404" else "200"
        val latencyMs = Random.nextInt(5, 300)

        return buildEvent(
            source = source,
            level = level,
            userId = userId,
            ip = domesticIp(),
            message = "$level ${source} ${endpoint} status=$statusCode latency=${latencyMs}ms",
            attributes = mapOf(
                "endpoint" to endpoint,
                "statusCode" to statusCode,
                "latencyMs" to latencyMs.toString(),
                "suspicious" to "false",
            ),
            timestamp = Instant.now(),
        )
    }

    // ─────────────────────────────── 공격 시나리오 ───────────────────────────────

    fun createSuspicious(type: AttackType): LogEvent = when (type) {
        AttackType.BRUTE_FORCE -> bruteForceLoginFailure()
        AttackType.SQL_INJECTION -> sqlInjectionAttempt()
        AttackType.ERROR_SPIKE -> errorSpike()
        AttackType.OFF_HOUR_ACCESS -> offHourAdminLogin()
        AttackType.GEO_ANOMALY -> geoAnomalyLogin()
        AttackType.RARE_EVENT -> rareEvent()
    }

    /** R001: auth-service 에서 동일 IP 의 로그인 실패가 반복되는 패턴 */
    private fun bruteForceLoginFailure(): LogEvent {
        val userId = normalUserIds.random()
        // IP 다양성을 억제해 슬라이딩 윈도우가 같은 IP로 계속 쌓이도록 유도
        val ip = "211.45.${Random.nextInt(0, 5)}.${Random.nextInt(1, 255)}"
        return buildEvent(
            source = "auth-service",
            level = "WARN",
            userId = userId,
            ip = ip,
            message = "authentication failed userId=$userId reason=invalid_password",
            attributes = mapOf(
                "action" to "login",
                "outcome" to "FAILURE",
                "reason" to "invalid_password",
                "attackType" to AttackType.BRUTE_FORCE.name,
                "suspicious" to "true",
            ),
            timestamp = Instant.now(),
        )
    }

    /** R002: message 에 SQLi 시그니처가 포함된 요청 로그 */
    private fun sqlInjectionAttempt(): LogEvent {
        val payload = sqlInjectionPayloads.random()
        val endpoint = "/api/products/search"
        return buildEvent(
            source = "api-gateway",
            level = "WARN",
            userId = normalUserIds.random(),
            ip = foreignIp(),
            message = "suspicious query endpoint=$endpoint query=name=$payload",
            attributes = mapOf(
                "endpoint" to endpoint,
                "payload" to payload,
                "attackType" to AttackType.SQL_INJECTION.name,
                "suspicious" to "true",
            ),
            timestamp = Instant.now(),
        )
    }

    /** R003: 특정 source 에서 ERROR 로그가 연속 발생하는 패턴 */
    private fun errorSpike(): LogEvent {
        val source = "payment-service"
        val reason = listOf(
            "upstream timeout",
            "connection reset",
            "500 internal server error",
            "circuit breaker OPEN",
        ).random()
        return buildEvent(
            source = source,
            level = "ERROR",
            userId = normalUserIds.random(),
            ip = domesticIp(),
            message = "$reason endpoint=/api/payments/charge",
            attributes = mapOf(
                "endpoint" to "/api/payments/charge",
                "statusCode" to "500",
                "reason" to reason,
                "attackType" to AttackType.ERROR_SPIKE.name,
                "suspicious" to "true",
            ),
            timestamp = Instant.now(),
        )
    }

    /** R004: 관리자 계정이 새벽 0~5시(KST) 에 로그인에 성공한 패턴 */
    private fun offHourAdminLogin(): LogEvent {
        val adminId = adminUserIds.random()
        val offHourTimestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            .withHour(Random.nextInt(0, 5))
            .withMinute(Random.nextInt(0, 60))
            .withSecond(Random.nextInt(0, 60))
            .toInstant()
        return buildEvent(
            source = "admin-portal",
            level = "INFO",
            userId = adminId,
            ip = domesticIp(),
            message = "admin login success userId=$adminId",
            attributes = mapOf(
                "action" to "login",
                "outcome" to "SUCCESS",
                "accountType" to "ADMIN",
                "attackType" to AttackType.OFF_HOUR_ACCESS.name,
                "suspicious" to "true",
            ),
            timestamp = offHourTimestamp,
        )
    }

    /** R005: 동일 userId 가 직전 로그인과 물리적으로 불가능한 거리(해외 IP)에서 접근 */
    private fun geoAnomalyLogin(): LogEvent {
        val userId = normalUserIds.random()
        return buildEvent(
            source = "auth-service",
            level = "INFO",
            userId = userId,
            ip = foreignIp(),
            message = "authentication success userId=$userId",
            attributes = mapOf(
                "action" to "login",
                "outcome" to "SUCCESS",
                "attackType" to AttackType.GEO_ANOMALY.name,
                "suspicious" to "true",
            ),
            timestamp = Instant.now(),
        )
    }

    /** R006: 평소 관측되지 않는 source+level+pattern 조합 */
    private fun rareEvent(): LogEvent {
        val rarePattern = "deprecated_handler_invoked"
        return buildEvent(
            source = RARE_SOURCE,
            level = "ERROR",
            userId = null,
            ip = domesticIp(),
            message = "rare pattern detected pattern=$rarePattern",
            attributes = mapOf(
                "pattern" to rarePattern,
                "attackType" to AttackType.RARE_EVENT.name,
                "suspicious" to "true",
            ),
            timestamp = Instant.now(),
        )
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    @Suppress("LongParameterList")
    private fun buildEvent(
        source: String,
        level: String,
        userId: Long?,
        ip: String,
        message: String,
        attributes: Map<String, String>,
        timestamp: Instant,
    ): LogEvent = LogEvent(
        id = null,
        transactionId = "EVT-${UUID.randomUUID()}",
        source = source,
        level = level,
        message = message,
        timestamp = timestamp,
        host = hostOf(source),
        ip = ip,
        userId = userId,
        attributes = attributes,
    )

    private fun hostOf(source: String): String =
        "$source-${"%02d".format(Random.nextInt(1, HOSTS_PER_SOURCE + 1))}"

    /** INFO 85% / WARN 10% / ERROR 5% — 정상 트래픽의 자연스러운 분포 */
    private fun randomNormalLevel(): String = when (Random.nextInt(100)) {
        in 0 until INFO_PORTION -> "INFO"
        in INFO_PORTION until INFO_PORTION + WARN_PORTION -> "WARN"
        else -> "ERROR"
    }

    private fun domesticIp(): String =
        "211.${Random.nextInt(0, 256)}.${Random.nextInt(0, 256)}.${Random.nextInt(1, 255)}"

    /** 해외 IP 대역 — GEO_ANOMALY, SQLi 소스로 사용 */
    private fun foreignIp(): String {
        val prefix = listOf("54", "124", "81", "51", "94").random()
        return "$prefix.${Random.nextInt(0, 256)}.${Random.nextInt(0, 256)}.${Random.nextInt(1, 255)}"
    }

    private const val NORMAL_USER_COUNT = 500L
    private const val ADMIN_USER_ID_START = 9_001L
    private const val ADMIN_USER_ID_END = 9_005L
    private const val HOSTS_PER_SOURCE = 4
    private const val INFO_PORTION = 85
    private const val WARN_PORTION = 10
}
