package com.gijun.logdetect.common.security

/**
 * service-to-service 인증 헤더 상수.
 *
 * WHY — log-ingest-service 의 `ApiKeyAuthenticationFilter` 와 log-generator 의 `IngestSendClientAdapter`
 * 양쪽이 동일한 헤더명을 사용해야 한다. 한쪽 모듈에서 상수를 바꾸면 다른 쪽이 깨지는 silent drift 를
 * 방지하기 위해 log-common 에 단일 source of truth 로 둔다.
 */
object ApiKeyConstants {
    /** X-API-Key — service-to-service 사전 공유키 헤더명. */
    const val HEADER_NAME = "X-API-Key"
}
