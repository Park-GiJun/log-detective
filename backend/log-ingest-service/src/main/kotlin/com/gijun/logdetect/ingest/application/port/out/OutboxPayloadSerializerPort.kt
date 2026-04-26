package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.common.domain.model.LogEvent

/**
 * Outbox payload 직렬화 포트.
 *
 * WHY — 핸들러가 [com.fasterxml.jackson.databind.ObjectMapper] 를 직접 의존하면
 * application 계층에 인프라(Jackson) 가 새어 들어온다. 포트로 격리하여
 * 직렬화 라이브러리 교체 / 보안 정책 (CVE-2017-7525 류 polymorphic typing 차단) 을
 * adapter 단에서 단일 책임으로 관리한다 (이슈 #45).
 */
fun interface OutboxPayloadSerializerPort {

    /**
     * LogEvent 를 Outbox 에 저장할 JSON 문자열로 직렬화한다.
     */
    fun serialize(event: LogEvent): String
}
