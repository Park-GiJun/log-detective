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

    /**
     * 시나리오 기반 공격 로그 합성.
     *
     * @param successful 시나리오 정의의 성공/실패 플래그. 모든 attackType 이 분기를 갖되 의미는 다음과 같다:
     *  - BRUTE_FORCE: true=결국 뚫림(SUCCESS), false=반복 실패(기본)
     *  - SQL_INJECTION: true=페이로드 통과(200), false=차단(403, 기본)
     *  - ERROR_SPIKE: true=복구 로그(INFO, "recovered"), false=ERROR 발생(기본)
     *  - OFF_HOUR_ACCESS: true=새벽 admin login 성공(기본), false=실패
     *  - GEO_ANOMALY: true=해외 로그인 성공(기본), false=실패
     *  - RARE_EVENT: true=정상 복귀(INFO), false=드문 ERROR(기본)
     */
    fun createSuspicious(type: AttackType, successful: Boolean): LogEvent = when (type) {
        AttackType.BRUTE_FORCE -> bruteForceLogin(successful)
        AttackType.SQL_INJECTION -> sqlInjectionAttempt(successful)
        AttackType.ERROR_SPIKE -> errorSpike(successful)
        AttackType.OFF_HOUR_ACCESS -> offHourAdminLogin(successful)
        AttackType.GEO_ANOMALY -> geoAnomalyLogin(successful)
        AttackType.RARE_EVENT -> rareEvent(successful)
    }

    /** R001: 동일 IP 반복. successful=true 면 brute force 가 뚫린 드문 케이스. */
    private fun bruteForceLogin(successful: Boolean): LogEvent {
        val userId = normalUserIds.random()
        val ip = "211.45.${Random.nextInt(0, 5)}.${Random.nextInt(1, 255)}"
        val outcome = if (successful) "SUCCESS" else "FAILURE"
        val level = if (successful) "WARN" else "WARN"
        val message = if (successful) {
            "authentication success userId=$userId after_repeated_failures=true"
        } else {
            "authentication failed userId=$userId reason=invalid_password"
        }
        return buildEvent(
            source = "auth-service",
            level = level,
            userId = userId,
            ip = ip,
            message = message,
            attributes = mapOf(
                "action" to "login",
                "outcome" to outcome,
                "reason" to if (successful) "credential_match" else "invalid_password",
                "attackType" to AttackType.BRUTE_FORCE.name,
                "suspicious" to "true",
            ),
            timestamp = Instant.now(),
        )
    }

    /** R002: SQLi 시그니처. successful=true 면 페이로드가 차단되지 않고 200 통과. */
    private fun sqlInjectionAttempt(successful: Boolean): LogEvent {
        val payload = sqlInjectionPayloads.random()
        val endpoint = "/api/products/search"
        val statusCode = if (successful) "200" else "403"
        val level = if (successful) "INFO" else "WARN"
        val outcome = if (successful) "PASSED" else "BLOCKED"
        return buildEvent(
            source = "api-gateway",
            level = level,
            userId = normalUserIds.random(),
            ip = foreignIp(),
            message = "suspicious query endpoint=$endpoint status=$statusCode query=name=$payload",
            attributes = mapOf(
                "endpoint" to endpoint,
                "payload" to payload,
                "statusCode" to statusCode,
                "outcome" to outcome,
                "attackType" to AttackType.SQL_INJECTION.name,
                "suspicious" to "true",
            ),
            timestamp = Instant.now(),
        )
    }

    /** R003: 특정 source 에서 ERROR 로그 연속 발생. successful=true 면 복구 로그. */
    private fun errorSpike(successful: Boolean): LogEvent {
        val source = "payment-service"
        val endpoint = "/api/payments/charge"
        val userId = normalUserIds.random()
        return if (successful) {
            buildEvent(
                source = source,
                level = "INFO",
                userId = userId,
                ip = domesticIp(),
                message = "recovered from upstream error endpoint=$endpoint",
                attributes = mapOf(
                    "endpoint" to endpoint,
                    "statusCode" to "200",
                    "outcome" to "RECOVERED",
                    "attackType" to AttackType.ERROR_SPIKE.name,
                    "suspicious" to "true",
                ),
                timestamp = Instant.now(),
            )
        } else {
            val reason = listOf(
                "upstream timeout",
                "connection reset",
                "500 internal server error",
                "circuit breaker OPEN",
            ).random()
            buildEvent(
                source = source,
                level = "ERROR",
                userId = userId,
                ip = domesticIp(),
                message = "$reason endpoint=$endpoint",
                attributes = mapOf(
                    "endpoint" to endpoint,
                    "statusCode" to "500",
                    "reason" to reason,
                    "outcome" to "FAILURE",
                    "attackType" to AttackType.ERROR_SPIKE.name,
                    "suspicious" to "true",
                ),
                timestamp = Instant.now(),
            )
        }
    }

    /** R004: 새벽 0~5시(KST) 관리자 접근. successful=false 면 시도만 실패. */
    private fun offHourAdminLogin(successful: Boolean): LogEvent {
        val adminId = adminUserIds.random()
        val offHourTimestamp = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
            .withHour(Random.nextInt(0, 5))
            .withMinute(Random.nextInt(0, 60))
            .withSecond(Random.nextInt(0, 60))
            .toInstant()
        val outcome = if (successful) "SUCCESS" else "FAILURE"
        val level = if (successful) "INFO" else "WARN"
        val message = if (successful) {
            "admin login success userId=$adminId"
        } else {
            "admin login failed userId=$adminId reason=invalid_password"
        }
        return buildEvent(
            source = "admin-portal",
            level = level,
            userId = adminId,
            ip = domesticIp(),
            message = message,
            attributes = mapOf(
                "action" to "login",
                "outcome" to outcome,
                "accountType" to "ADMIN",
                "attackType" to AttackType.OFF_HOUR_ACCESS.name,
                "suspicious" to "true",
            ),
            timestamp = offHourTimestamp,
        )
    }

    /** R005: 해외 IP 접근. successful=false 면 인증 실패. */
    private fun geoAnomalyLogin(successful: Boolean): LogEvent {
        val userId = normalUserIds.random()
        val outcome = if (successful) "SUCCESS" else "FAILURE"
        val level = if (successful) "INFO" else "WARN"
        val message = if (successful) {
            "authentication success userId=$userId"
        } else {
            "authentication failed userId=$userId reason=invalid_password"
        }
        return buildEvent(
            source = "auth-service",
            level = level,
            userId = userId,
            ip = foreignIp(),
            message = message,
            attributes = mapOf(
                "action" to "login",
                "outcome" to outcome,
                "attackType" to AttackType.GEO_ANOMALY.name,
                "suspicious" to "true",
            ),
            timestamp = Instant.now(),
        )
    }

    /** R006: 드문 source+level+pattern 조합. successful=true 면 정상 복귀(INFO), false 면 ERROR. */
    private fun rareEvent(successful: Boolean): LogEvent {
        val rarePattern = "deprecated_handler_invoked"
        val level = if (successful) "INFO" else "ERROR"
        val outcome = if (successful) "RECOVERED" else "FAILURE"
        val message = if (successful) {
            "rare pattern recovered pattern=$rarePattern"
        } else {
            "rare pattern detected pattern=$rarePattern"
        }
        return buildEvent(
            source = RARE_SOURCE,
            level = level,
            userId = null,
            ip = domesticIp(),
            message = message,
            attributes = mapOf(
                "pattern" to rarePattern,
                "outcome" to outcome,
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
