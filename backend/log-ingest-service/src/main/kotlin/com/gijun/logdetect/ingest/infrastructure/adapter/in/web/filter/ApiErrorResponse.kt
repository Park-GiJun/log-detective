package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.web.filter

/**
 * 인증 실패 시 [ApiKeyAuthenticationFilter] 가 직렬화해 응답하는 에러 본문.
 *
 * WHY — raw 문자열 리터럴(`"""{"error":"..."}"""`) 대신 type-safe 한 데이터 클래스로 두면
 * 필드명 변경 / 추가 시 컴파일러가 보장하고, ObjectMapper 가 escaping 도 책임진다.
 * 향후 표준 에러 포맷(RFC 7807 등)으로 확장할 때 단일 진입점이 된다.
 */
data class ApiErrorResponse(
    val error: String,
    val message: String,
)
