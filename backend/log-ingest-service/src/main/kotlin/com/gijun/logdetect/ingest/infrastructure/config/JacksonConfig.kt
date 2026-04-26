package com.gijun.logdetect.ingest.infrastructure.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator
import org.springframework.beans.factory.config.BeanPostProcessor
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
 *
 * 이슈 #96 — `Jackson2ObjectMapperBuilderCustomizer` 만으로는 typing 정책 잠금이
 * 약하다 (사후 코드가 mapper 에 다시 `activateDefaultTyping` 을 호출할 수 있음).
 * `BeanPostProcessor` 로 모든 ObjectMapper 빈을 가로채 다음 두 가지를 강제 적용한다:
 *
 *   1. `deactivateDefaultTyping()` — default typing 을 명시적으로 끔
 *   2. `setPolymorphicTypeValidator(deny-all)` — 명시 polymorphism (e.g., `@JsonTypeInfo`)
 *      이 사용되더라도 모든 서브타입을 거부하는 validator 로 잠금
 *
 * 결과 — 누군가 customizer 외부에서 `activateDefaultTyping` 을 호출하더라도, validator
 * 가 deny-all 이므로 실제 역직렬화 시 `InvalidDefinitionException` 으로 실패한다.
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
                DeserializationFeature.FAIL_ON_INVALID_SUBTYPE,
            )
            builder.featuresToDisable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
            )
        }

    /**
     * 모든 ObjectMapper 빈에 대해 polymorphic typing 정책을 코드로 강제 적용한다.
     *
     * WHY — customizer 는 builder 단계의 정책일 뿐, 빈 등록 후 누군가 ObjectMapper 를
     * 직접 가져와 `activateDefaultTyping` 을 호출하면 무력화된다. BeanPostProcessor
     * 시점에 deactivate + deny-all validator 를 박으면, 사후 호출이 들어와도
     * validator 가 모든 서브타입을 거부한다.
     */
    @Bean
    fun jacksonObjectMapperPolicyEnforcer(): BeanPostProcessor = JacksonPolicyEnforcer()

    /**
     * 별도 클래스로 분리한 이유 — BeanPostProcessor 를 람다로 등록하면 Spring 의 일부
     * AOT/네이티브 처리에서 타입 추론이 어긋날 수 있어, 명명 클래스로 둔다.
     */
    class JacksonPolicyEnforcer : BeanPostProcessor {
        override fun postProcessAfterInitialization(bean: Any, beanName: String): Any {
            if (bean is ObjectMapper) {
                // 1) Default typing 명시적 비활성화. 사후에 다시 activateDefaultTyping 을 부르면
                //    validator (2) 가 거부 트리거를 잡는다.
                bean.deactivateDefaultTyping()

                // 2) deny-all PolymorphicTypeValidator —
                //    BasicPolymorphicTypeValidator 를 룰 등록 없이 build() 하면 모든 후보를
                //    거부하는 validator 가 된다. LaissezFaireSubTypeValidator 의 정반대.
                bean.setPolymorphicTypeValidator(DENY_ALL_VALIDATOR)
            }
            return bean
        }

        companion object {
            /**
             * 빌더에 어떤 allow* 룰도 등록하지 않은 채 build() 하면, 모든 base type / subtype
             * 후보가 INDETERMINATE 로 평가되어 Jackson 이 거부한다 (deny-all 동등).
             *
             * 비교 — `LaissezFaireSubTypeValidator` 는 모든 타입을 허용 (=구버전 디폴트, RCE 위험).
             * 본 validator 는 그 정반대 — 명시 polymorphism 이 등장하더라도 모두 거부한다.
             */
            val DENY_ALL_VALIDATOR: PolymorphicTypeValidator =
                BasicPolymorphicTypeValidator.builder().build()
        }
    }
}
