package com.gijun.logdetect.ingest.application.port.out

/**
 * 외부 시스템 (ES / Kafka / DB) 예외 메시지에 섞인 자격 증명 / PII / 토큰을 마스킹하기 위한
 * 아웃바운드 포트.
 *
 * WHY — application 계층이 인프라(ErrorRedactor 객체) 를 직접 참조하지 않도록 포트를 분리한다.
 * outbox 의 last_error 컬럼에 평문 자격 증명이 흘러 들어가는 것을 막는 1차 방어선.
 *
 * 위치 일관성 (이슈 #97) — 다른 outbound port 와 동일하게 `application/port/out/` 에 둔다.
 * 구현체는 `infrastructure/util/ErrorRedactor.kt` (`@Component`).
 */
fun interface ErrorRedactorPort {
    fun redact(input: String): String
}
