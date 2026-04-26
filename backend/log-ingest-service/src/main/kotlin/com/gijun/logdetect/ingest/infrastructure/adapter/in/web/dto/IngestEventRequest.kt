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

    val ip: String? = null,
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
    }
}
