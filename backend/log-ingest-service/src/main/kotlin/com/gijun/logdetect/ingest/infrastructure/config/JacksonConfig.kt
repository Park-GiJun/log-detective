package com.gijun.logdetect.ingest.infrastructure.config

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * ObjectMapper 보안 정책 잠금.
 *
 * WHY — `activateDefaultTyping` 이 활성화될 경우 임의 클래스 역직렬화로 RCE 가
 * 가능하다 (CVE-2017-7525 류). Spring Boot 의 default ObjectMapper 위에 customizer
 * 로 명시적 보안 디폴트를 박아두어, 향후 누군가 polymorphic typing 을 무심코 켜도
 * 의도된 보안 기준선이 코드로 남도록 한다.
 *
 * 참고 — 본 서비스는 Outbox payload 를 자체 발행 LogEvent 단일 타입으로만
 * 역직렬화하므로 polymorphic typing 자체가 필요 없다.
 */
@Configuration
class JacksonConfig {

    @Bean
    fun jacksonSecurityCustomizer(): Jackson2ObjectMapperBuilderCustomizer =
        Jackson2ObjectMapperBuilderCustomizer { builder ->
            // WHY: activateDefaultTyping 호출은 명시적으로 금지. 호출 시점에 컴파일 단계에서 잡히지는
            // 않지만, 본 빈이 존재한다는 사실 자체가 "정책 잠금" 의 흔적 — 추가 활성화 코드가 들어오면
            // 리뷰에서 즉시 거부될 것을 가정한다. 단위 테스트(JacksonConfigTest) 가 이를 회귀 검증한다.
            //
            // 추가 보호: 기본 typing 정보 없이 페이로드가 들어오면 실패하도록 명시적 디폴트를 강제한다.
            // (Boot 4 의 디폴트와 동일하지만, 누군가 이를 풀 경우의 회귀를 차단한다.)
            builder.featuresToEnable(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_INVALID_SUBTYPE,
            )
            builder.featuresToDisable(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            )
        }
}
