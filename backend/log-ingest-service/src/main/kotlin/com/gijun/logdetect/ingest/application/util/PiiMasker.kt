package com.gijun.logdetect.ingest.application.util

import com.gijun.logdetect.common.domain.model.LogEvent

/**
 * Outbox payload 적재 전 LogEvent 의 PII 필드를 마스킹하기 위한 유틸.
 *
 * WHY — `outbox_messages.payload (jsonb)` 에 LogEvent 전체 (ip, userId, message,
 * attributes) 가 평문으로 저장되면, retention 잡 누락 시 GDPR / 개인정보보호법
 * 보관 기한을 초과한다. 1차 방어선으로 정규식/규칙 기반 마스킹을 둔다.
 *
 * 운영 정책 — 다운스트림 (ES indexing, Kafka 컨슈머) 이 마스킹된 데이터를 받게
 * 되므로 활성화 여부는 운영 결정에 위임한다 (`logdetect.outbox.payload-pii-mask`).
 * 기본은 OFF 이며, 코드 차원에서는 언제든 켤 수 있도록 준비만 해둔다.
 *
 * 위치 — 도메인 모델 (LogEvent) 변환 정책이며 프레임워크 의존이 없으므로 application/util
 * 에 둔다. handler 에서 직접 호출하기 위해 application 계층에 위치시켰다.
 */
object PiiMasker {

    private const val IP_LAST_OCTET_PLACEHOLDER = "*"
    private const val MASK_KEEP_HEAD = 4
    private const val MASK_KEEP_TAIL = 0
    private val IPV4_OCTETS = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    /**
     * IPv4 의 마지막 옥텟을 `*` 로 치환한다 (예: `10.0.13.42` → `10.0.13.*`).
     *
     * IPv6 / 비표준 포맷은 정규식 미스매치 시 그대로 반환 — 호출 측에서 ip 가
     * null 일 가능성도 있으므로 nullable 입력을 그대로 받는다.
     */
    fun maskIp(ip: String?): String? {
        if (ip.isNullOrBlank()) return ip
        val match = IPV4_OCTETS.matchEntire(ip) ?: return ip
        val (a, b, c, _) = match.destructured
        return "$a.$b.$c.$IP_LAST_OCTET_PLACEHOLDER"
    }

    /**
     * userId 의 앞 [MASK_KEEP_HEAD] 글자만 보존하고 나머지는 `*` 로 치환한다.
     * 예: `user-123-1234` → `user*********` (앞 4자 보존, 나머지 길이만큼 `*`).
     *
     * 짧은 (≤ 4글자) 식별자는 전부 `*` 처리하여 prefix 노출도 막는다.
     */
    fun maskUserId(userId: String?): String? {
        if (userId.isNullOrBlank()) return userId
        if (userId.length <= MASK_KEEP_HEAD) return "*".repeat(userId.length)
        val head = userId.take(MASK_KEEP_HEAD)
        val tailLen = userId.length - MASK_KEEP_HEAD - MASK_KEEP_TAIL
        return head + "*".repeat(tailLen)
    }

    /**
     * LogEvent 의 PII 필드 (ip, userId) 만 마스킹한 사본을 반환한다.
     * message / attributes 는 운영 결정 — 본 1차 방어선에서는 식별자 위주로만
     * 적용하여 다운스트림 호환성을 유지한다.
     */
    fun mask(event: LogEvent): LogEvent =
        event.copy(
            ip = maskIp(event.ip),
            userId = maskUserId(event.userId),
        )
}
