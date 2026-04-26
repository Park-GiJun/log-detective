package com.gijun.logdetect.ingest.infrastructure.util

import com.gijun.logdetect.ingest.application.port.out.ErrorRedactorPort
import org.springframework.stereotype.Component

/**
 * 외부 시스템 (ES / Kafka / DB 등) 예외 메시지에 섞일 수 있는 호스트, 자격 증명,
 * JWT, 쿼리 토큰, 클라우드 키, PII 등을 마스킹하여 outbox.last_error 에 안전하게 저장하기 위한 어댑터.
 *
 * WHY — 예외 메시지가 그대로 DB 에 남으면 운영자가 콘솔에서 마주칠 수 있고,
 * 외부 로그 수집 파이프라인에 흘러 들어가면 사고로 이어진다. 1차 방어선으로
 * 정규식 기반 redaction 을 둔다.
 *
 * 위치 — application 의 `ErrorRedactorPort` 구현체 (이슈 #97). Spring `@Component` 로
 * 빈 등록되며 HandlerConfig 에서 직접 주입된다.
 */
@Component
class ErrorRedactor : ErrorRedactorPort {

    override fun redact(input: String): String {
        // 순서 주의 — 더 긴/구체적 패턴을 먼저 매치해 짧은 패턴(IPv4 등) 이 토큰 내부 숫자를 잘라먹지 않게 한다.
        var out = input
        out = JWT.replace(out, "[REDACTED-JWT]")
        out = AWS_ACCESS_KEY.replace(out, "[REDACTED-AWS-AK]")
        out = GCP_API_KEY.replace(out, "[REDACTED-GCP]")
        out = AWS_SECRET_KEY.replace(out) { match ->
            val key = match.groupValues[1]
            "$key=[REDACTED-AWS-SK]"
        }
        out = AUTHORIZATION_HEADER.replace(out) { match ->
            val key = match.groupValues[1]
            "$key [REDACTED]"
        }
        out = URL_CREDENTIAL.replace(out, "://***@")
        out = QUERY_SECRET.replace(out) { match ->
            val sep = match.groupValues[1]
            val key = match.groupValues[2]
            "$sep$key=[REDACTED]"
        }
        out = RRN.replace(out, "[REDACTED-RRN]")
        out = EMAIL.replace(out, "[REDACTED-EMAIL]")
        out = PHONE.replace(out, "[REDACTED-PHONE]")
        out = IPV4.replace(out, "***.***.***.***")
        return out
    }

    companion object {
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

        // AWS Access Key ID — 확정 prefix `AKIA` + 16자 대문자/숫자.
        private val AWS_ACCESS_KEY = Regex("""\bAKIA[0-9A-Z]{16}\b""")

        // AWS Secret Access Key — 일반 패턴은 너무 광범위해 false positive 가 많다.
        // 키 이름이 명시된 경우만 (`aws_secret_access_key=...` / `secret_key=...`) 값을 마스킹.
        // base64 + `/` + `+` 까지 허용, 40자 길이.
        private val AWS_SECRET_KEY = Regex(
            """(?i)\b(aws[_-]?secret[_-]?access[_-]?key|secret[_-]?access[_-]?key|secret[_-]?key)=[A-Za-z0-9/+=]{40}\b""",
        )

        // GCP API Key — `AIza` prefix + 35자.
        private val GCP_API_KEY = Regex("""\bAIza[0-9A-Za-z_\-]{35}\b""")

        // Authorization 헤더 / Bearer / Basic — 헤더명만 보존하고 값 마스킹.
        // ES / Kafka 클라이언트가 throw 할 때 헤더 dump 가 섞여 들어오는 경우를 가정한다.
        private val AUTHORIZATION_HEADER = Regex(
            """(?i)\b(authorization|bearer|basic)[ :=]+[A-Za-z0-9._\-+/=]+""",
        )

        // 한국 주민등록번호 — `YYMMDD-[1-4]NNNNNN`.
        private val RRN = Regex("""\b\d{6}-[1-4]\d{6}\b""")

        // 이메일 — 일반적인 RFC 5322 의 sub-set. 도메인 라벨 1자 이상 + TLD.
        private val EMAIL = Regex("""\b[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}\b""")

        // 한국 전화번호 — `0XX-XXXX-XXXX` / `0XX XXXX XXXX` / `0XXXXXXXXXX`.
        // IPv4 와 충돌하지 않도록 IPv4 마스킹 이전에 실행한다 (전화번호 `010-1234-5678` 은 IP 가 아님).
        private val PHONE = Regex("""\b0\d{1,2}[\- ]?\d{3,4}[\- ]?\d{4}\b""")
    }
}
