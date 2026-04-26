package com.gijun.logdetect.ingest.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 이슈 #110 — log-ingest-service 인증 설정.
 *
 *  - apiKey       : 단일 키 (legacy / backward compat). 빈 값 허용.
 *  - apiKeys      : comma-separated 키 목록. 각 항목은 다음 형식.
 *                   - "key"          → clientId 미지정 (default 사용)
 *                   - "clientId:key" → clientId 매핑
 *  - failureLimit : 락아웃 임계 (실패 횟수)
 *  - failureWindowSeconds : 카운터 만료 윈도우
 *  - lockoutSeconds       : 락아웃 지속 시간
 *
 * application.yml 의 `logdetect.ingest` prefix 와 동일.
 * `apiKey` (단일) + `apiKeys` (리스트) 둘 다 지원하며 둘 다 있으면 합쳐 사용.
 */
@ConfigurationProperties(prefix = "logdetect.ingest")
data class IngestSecurityProperties(
    val apiKey: String = "",
    val apiKeys: List<String> = emptyList(),
    val failureLimit: Int = DEFAULT_FAILURE_LIMIT,
    val failureWindowSeconds: Long = DEFAULT_FAILURE_WINDOW_SECONDS,
    val lockoutSeconds: Long = DEFAULT_LOCKOUT_SECONDS,
) {
    companion object {
        const val DEFAULT_FAILURE_LIMIT = 5
        const val DEFAULT_FAILURE_WINDOW_SECONDS = 300L
        const val DEFAULT_LOCKOUT_SECONDS = 300L
    }
}
