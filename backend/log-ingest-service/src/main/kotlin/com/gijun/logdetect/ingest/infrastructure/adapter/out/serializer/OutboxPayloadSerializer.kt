package com.gijun.logdetect.ingest.infrastructure.adapter.out.serializer

import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.OutboxPayloadSerializerPort
import org.springframework.stereotype.Component

/**
 * Jackson 기반 [OutboxPayloadSerializerPort] 구현.
 *
 * WHY — Spring Boot 가 구성한 ObjectMapper (JacksonConfig 의 보안 디폴트 적용) 를
 * 그대로 재사용해 직렬화 정책 일관성을 확보한다.
 */
@Component
class OutboxPayloadSerializer(
    private val objectMapper: ObjectMapper,
) : OutboxPayloadSerializerPort {

    override fun serialize(event: LogEvent): String =
        objectMapper.writeValueAsString(event)
}
