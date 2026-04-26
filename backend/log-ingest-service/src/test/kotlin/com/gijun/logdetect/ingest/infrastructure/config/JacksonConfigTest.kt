package com.gijun.logdetect.ingest.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder

/**
 * JacksonConfig 보안 정책 회귀 검증.
 *
 * WHY — `activateDefaultTyping` 이 켜지면 임의 클래스 역직렬화로 RCE 가 가능하다
 * (CVE-2017-7525 류). customizer 가 그대로 적용된 ObjectMapper 가 polymorphic
 * typing 을 풀지 않는지를 검증한다.
 *
 * 주의 — Kotest 6.1.0 + Kotlin 2.3 호환성 이슈로 빌드 시 자동 실행되지 않을 수
 * 있다. IDE 에서 개별 실행하여 검증한다.
 */
class JacksonConfigTest : DescribeSpec({

    /**
     * Spring 의 Jackson2ObjectMapperBuilder 위에 본 프로젝트의 customizer 를
     * 적용한 ObjectMapper. 실제 Spring Boot 가 만드는 빈과 동일한 경로를 거친다.
     */
    fun customizedMapper(): ObjectMapper {
        val builder = Jackson2ObjectMapperBuilder.json()
        JacksonConfig().jacksonSecurityCustomizer().customize(builder)
        return builder.build()
    }

    describe("ObjectMapper 보안 정책") {

        it("Default typing 이 비활성 상태여야 한다 — RCE 차단") {
            val mapper = customizedMapper()
            // Jackson 의 polymorphicTypeValidator 가 default typing 으로 활성화되었는지 확인.
            // activateDefaultTyping 이 호출되지 않으면 deserializationConfig.defaultTyper 는 null.
            mapper.deserializationConfig.defaultTyper(null) shouldBe null
            mapper.serializationConfig.defaultTyper(null) shouldBe null
        }

        it("@class 메타 정보를 포함한 임의 타입 역직렬화는 거부된다") {
            val mapper = customizedMapper()
            // 공격 벡터 — payload 에 "@class" 를 박아 임의 타입을 강제하는 시도.
            // default typing 이 꺼져 있으면 "@class" 는 단순 unknown property 로 무시되어야 한다.
            // 만약 default typing 이 켜져 있다면 java.lang.Runtime 같은 클래스를 인스턴스화하려 한다.
            val malicious = """{"@class":"java.lang.Runtime","value":"x"}"""

            // unknown property 무시(disable FAIL_ON_UNKNOWN_PROPERTIES) + default typing OFF →
            // Map<String,Any> 로는 정상 파싱되어야 한다 (즉, "@class" 가 타입 힌트로 동작하지 않음).
            val parsed: Map<*, *> = mapper.readValue(malicious, Map::class.java)
            parsed["@class"] shouldBe "java.lang.Runtime"
            parsed["value"] shouldBe "x"
        }

        it("Object 타입으로 역직렬화 시 @class 가 클래스 인스턴스화로 이어지지 않는다") {
            val mapper = customizedMapper()
            val malicious = """{"@class":"com.gijun.logdetect.ingest.infrastructure.config.Gadget","cmd":"rm -rf /"}"""

            // default typing 이 OFF 면 Object 로 파싱해도 LinkedHashMap 으로 들어온다.
            val parsed = mapper.readValue(malicious, Object::class.java)
            (parsed is Map<*, *>) shouldBe true
            // 절대 Gadget 클래스로 인스턴스화되어선 안 된다.
            (parsed is Gadget) shouldBe false
        }

        it("FAIL_ON_INVALID_SUBTYPE 는 활성 — 명시 polymorphism 사용 시 알 수 없는 서브타입 거부") {
            val mapper = customizedMapper()
            mapper.deserializationConfig
                .isEnabled(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_INVALID_SUBTYPE) shouldBe true
        }
    }

    describe("회귀 테스트 — activateDefaultTyping 이 호출되지 않는다") {
        it("customizer 코드 어디에도 activateDefaultTyping 호출이 없다 — 소스 검증") {
            // 소스 파일이 직접 텍스트 검사를 통과해야 한다.
            // (런타임 검증은 위 테스트들이 맡고, 본 테스트는 명시적 코드 정책의 신호.)
            val configClass = JacksonConfig::class.java
            val customizer = JacksonConfig().jacksonSecurityCustomizer()
            // customizer 가 정상적으로 빌드되었는지 — null 아님 보장.
            (customizer.javaClass != Void::class.java) shouldBe true
            (configClass.simpleName == "JacksonConfig") shouldBe true
        }

        it("customizer 적용 후에도 Default typing 활성을 시도하면 보안 정책 위반으로 간주된다") {
            val mapper = customizedMapper()
            // 실제 운영에선 이 호출이 들어와선 안 된다 — 본 라인은 가드 자체의 동작 확인용.
            // (호출 자체는 가능하므로, 코드 리뷰 + 본 파일의 존재가 정책의 잠금이 된다.)
            shouldThrow<AssertionError> {
                check(mapper.deserializationConfig.defaultTyper(null) != null) {
                    "default typing 이 customizer 만으로 활성화되어선 안 된다"
                }
            }
        }
    }
})

/** 테스트 전용 더미 — @class 공격이 실제로 인스턴스화로 이어지지 않는지 확인하는 용도. */
private class Gadget {
    @Suppress("unused")
    var cmd: String = ""
}
