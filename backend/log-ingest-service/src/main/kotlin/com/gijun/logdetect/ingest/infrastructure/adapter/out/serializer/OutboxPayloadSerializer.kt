package com.gijun.logdetect.ingest.infrastructure.adapter.out.serializer

import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.OutboxPayloadSerializerPort
import com.gijun.logdetect.ingest.application.util.PiiMasker
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Jackson 기반 [OutboxPayloadSerializerPort] 구현.
 *
 * WHY — Spring Boot 가 구성한 ObjectMapper (JacksonConfig 의 보안 디폴트 적용) 를
 * 그대로 재사용해 직렬화 정책 일관성을 확보한다.
 *
 * PII 마스킹 토글 (이슈 #49):
 * outbox.payload 직렬화 직전 LogEvent 의 PII 필드 (ip, userId) 를 마스킹할지 여부.
 * 기본 false — 다운스트림 (ES / Kafka) 이 평문을 받도록 운영 결정에 위임.
 * true 로 켜면 outbox.payload 가 마스킹되어 저장되고, dispatch 시 그대로 전송된다.
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
) : OutboxPayloadSerializerPort {

    override fun serialize(event: LogEvent): String {
        val target = if (piiMaskPayload) PiiMasker.mask(event) else event
        return objectMapper.writeValueAsString(target)
    }
}
