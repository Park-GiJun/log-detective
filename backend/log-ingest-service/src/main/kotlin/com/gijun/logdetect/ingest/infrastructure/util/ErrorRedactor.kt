package com.gijun.logdetect.ingest.infrastructure.util

/**
 * 외부 시스템 (ES / Kafka / DB 등) 예외 메시지에 섞일 수 있는 호스트, 자격 증명,
 * JWT, 쿼리 토큰 등을 마스킹하여 outbox.last_error 에 안전하게 저장하기 위한 유틸.
 *
 * WHY — 예외 메시지가 그대로 DB 에 남으면 운영자가 콘솔에서 마주칠 수 있고,
 * 외부 로그 수집 파이프라인에 흘러 들어가면 사고로 이어진다. 1차 방어선으로
 * 정규식 기반 redaction 을 둔다.
 */
object ErrorRedactor {

    private val IPV4 = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")

    // user:pass@host 의 자격 증명 부분 — scheme 보존, host 만 살린다.
    private val URL_CREDENTIAL = Regex("""://[^/\s:@]+:[^/\s@]+@""")

    // JWT 3-segment 패턴 — header.payload.signature
    private val JWT = Regex("""eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}""")

    // ?token=... / &api_key=... / ?password=... — 키 이름은 보존, 값만 마스킹
    private val QUERY_SECRET = Regex(
        """([?&])(token|api[_-]?key|password)=[^&\s]+""",
        RegexOption.IGNORE_CASE,
    )

    fun redact(input: String): String {
        // 순서 주의 — JWT 가 IPv4 보다 먼저 매치되어야 토큰 내부 숫자가 IP 로 오인되지 않는다.
        var out = input
        out = JWT.replace(out, "[REDACTED]")
        out = URL_CREDENTIAL.replace(out, "://***@")
        out = QUERY_SECRET.replace(out) { match ->
            val sep = match.groupValues[1]
            val key = match.groupValues[2]
            "$sep$key=[REDACTED]"
        }
        out = IPV4.replace(out, "***.***.***.***")
        return out
    }
}
