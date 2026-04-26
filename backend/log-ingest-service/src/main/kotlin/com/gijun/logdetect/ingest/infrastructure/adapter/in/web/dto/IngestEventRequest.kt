package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.dto

import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.ingest.application.dto.command.IngestEventCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * 입력 검증 정책:
 *  - source/level/message 는 NotBlank
 *  - level 은 LogLevel enum (TRACE/DEBUG/INFO/WARN/ERROR/FATAL) 만 허용 — 대소문자 무시
 *  - source/host 는 길이 상한, message 는 16384 (DoS / 비정상 페이로드 방어)
 *  - ip 는 IPv4 단순 dotted-quad 또는 IPv6 hex-colon 패턴만 허용 (이슈 #99)
 *    — 형식 검증은 1차 방어선이고, 라우팅/탐지 정책은 별도 모듈이 책임진다.
 *  - userId 는 외부 식별자 — 길이만 제한 (128). 형식은 시스템마다 다르므로 풀어둠.
 */
data class IngestEventRequest(
    @field:NotBlank
    @field:Size(max = MAX_SOURCE_LENGTH)
    val source: String,

    @field:NotBlank
    @field:Pattern(
        regexp = "(?i)TRACE|DEBUG|INFO|WARN|ERROR|FATAL",
        message = "level 은 TRACE/DEBUG/INFO/WARN/ERROR/FATAL 중 하나여야 합니다",
    )
    val level: String,

    @field:NotBlank
    @field:Size(max = MAX_MESSAGE_LENGTH)
    val message: String,

    val timestamp: Instant? = null,

    @field:Size(max = MAX_HOST_LENGTH)
    val host: String? = null,

    @field:Size(max = MAX_IP_LENGTH)
    @field:Pattern(
        regexp = IP_PATTERN,
        message = "ip 는 IPv4 dotted-quad 또는 IPv6 hex-colon 형식이어야 합니다",
    )
    val ip: String? = null,

    @field:Size(max = MAX_USER_ID_LENGTH)
    val userId: String? = null,

    val attributes: Map<String, String>? = null,
) {
    fun toCommand(): IngestEventCommand = IngestEventCommand(
        source = source,
        // 검증 통과 후이므로 LogLevel.valueOf 는 안전. 대문자 정규화는 핸들러가 수행하지만,
        // 여기서도 미리 normalize 하여 어떤 경로로 들어와도 일관되게 처리한다.
        level = LogLevel.valueOf(level.uppercase()).name,
        message = message,
        timestamp = timestamp ?: Instant.now(),
        host = host,
        ip = ip,
        userId = userId,
        attributes = attributes ?: emptyMap(),
    )

    companion object {
        const val MAX_SOURCE_LENGTH = 128
        const val MAX_HOST_LENGTH = 255
        const val MAX_MESSAGE_LENGTH = 16384
        const val MAX_IP_LENGTH = 45 // IPv6 최대 표현 (39 자) + 여유
        const val MAX_USER_ID_LENGTH = 128

        // IPv4 — 0~255 범위까지 엄밀히 잡지는 않고 자릿수 + 구분자만 본다 (1차 방어선).
        // IPv6 — 압축 표현(::), 혼합 표현(IPv4-mapped) 까지 단순 허용.
        // jakarta.validation @Pattern 은 부분 매치가 아닌 전체 매치이므로 ^/$ 불필요.
        private const val IPV4_REGEX = """(\d{1,3}\.){3}\d{1,3}"""
        private const val IPV6_REGEX = """([0-9a-fA-F:]+(:[0-9a-fA-F]+)*|::1)"""
        const val IP_PATTERN = "$IPV4_REGEX|$IPV6_REGEX"
    }
}
