package com.gijun.logdetect.ingest.infrastructure.adapter.out.serializer

import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.OutboxPayloadSerializerPort
import com.gijun.logdetect.ingest.application.util.PiiMasker
import com.gijun.logdetect.ingest.application.util.PiiMode
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Jackson 기반 [OutboxPayloadSerializerPort] 구현.
 *
 * WHY — Spring Boot 가 구성한 ObjectMapper (JacksonConfig 의 보안 디폴트 적용) 를
 * 그대로 재사용해 직렬화 정책 일관성을 확보한다.
 *
 * ### PII 마스킹 토글 (이슈 #49 / #88)
 * outbox.payload 직렬화 직전 LogEvent 의 PII 필드 (ip, userId) 를 어떻게 변환할지 결정한다.
 * - `payload-pii-mask=false` (default) — 평문 직렬화 (다운스트림 호환 우선).
 * - `payload-pii-mask=true` + `pii.mode=MASK` — IPv4 `/16`, IPv6 앞 64bit, userId
 *   길이 보존 `*` 마스킹 (가독성 유지, GDPR 강화).
 * - `payload-pii-mask=true` + `pii.mode=HASH` — HMAC-SHA256(`pii.salt`) deterministic
 *   pseudonymization. 다운스트림 그룹화 가능, salt 회전 필요.
 *
 * 위치 결정 — 마스킹은 인프라 직렬화 정책의 일부이므로 application 계층 (handler) 이
 * 아닌 Serializer 어댑터에 둔다. application 계층은 토글 존재 자체를 모르며,
 * `OutboxPayloadSerializerPort.serialize(event)` 만 호출한다.
 */
@Component
class OutboxPayloadSerializer(
    private val objectMapper: ObjectMapper,
    @Value("\${logdetect.outbox.payload-pii-mask:false}")
    private val piiMaskPayload: Boolean,
    @Value("\${logdetect.outbox.pii.mode:MASK}")
    private val piiMode: PiiMode = PiiMode.MASK,
    @Value("\${logdetect.outbox.pii.salt:}")
    private val piiSalt: String = "",
) : OutboxPayloadSerializerPort {

    override fun serialize(event: LogEvent): String {
        val target = if (piiMaskPayload) PiiMasker.mask(event, piiMode, piiSalt) else event
        return objectMapper.writeValueAsString(target)
    }
}
