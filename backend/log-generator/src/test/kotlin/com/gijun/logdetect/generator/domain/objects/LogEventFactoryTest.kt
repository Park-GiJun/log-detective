package com.gijun.logdetect.generator.domain.objects

import com.gijun.logdetect.generator.domain.enums.AttackType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class LogEventFactoryTest : DescribeSpec({

    describe("createNormal") {
        it("attackType 속성이 없고 suspicious=false 로 마킹된다") {
            val event = LogEventFactory.createNormal()
            event.attributes["suspicious"] shouldBe "false"
            event.attributes["attackType"] shouldBe null
        }
    }

    describe("createSuspicious — 공통 불변식") {
        AttackType.entries.forEach { type ->
            listOf(true, false).forEach { successful ->
                it("$type / successful=$successful → attackType 와 suspicious=true 가 항상 기록된다") {
                    val event = LogEventFactory.createSuspicious(type, successful)
                    event.attributes["attackType"] shouldBe type.name
                    event.attributes["suspicious"] shouldBe "true"
                    event.transactionId shouldNotBe null
                }
            }
        }
    }

    describe("BRUTE_FORCE 분기") {
        it("successful=true → outcome=SUCCESS, message 에 success 단어 포함") {
            val e = LogEventFactory.createSuspicious(AttackType.BRUTE_FORCE, successful = true)
            e.attributes["outcome"] shouldBe "SUCCESS"
            e.message shouldContain "success"
        }
        it("successful=false → outcome=FAILURE, reason=invalid_password") {
            val e = LogEventFactory.createSuspicious(AttackType.BRUTE_FORCE, successful = false)
            e.attributes["outcome"] shouldBe "FAILURE"
            e.attributes["reason"] shouldBe "invalid_password"
        }
    }

    describe("SQL_INJECTION 분기") {
        it("successful=true → statusCode=200, level=INFO, outcome=PASSED") {
            val e = LogEventFactory.createSuspicious(AttackType.SQL_INJECTION, successful = true)
            e.attributes["statusCode"] shouldBe "200"
            e.attributes["outcome"] shouldBe "PASSED"
            e.level shouldBe "INFO"
        }
        it("successful=false → statusCode=403, level=WARN, outcome=BLOCKED") {
            val e = LogEventFactory.createSuspicious(AttackType.SQL_INJECTION, successful = false)
            e.attributes["statusCode"] shouldBe "403"
            e.attributes["outcome"] shouldBe "BLOCKED"
            e.level shouldBe "WARN"
        }
    }

    describe("ERROR_SPIKE 분기") {
        it("successful=true → level=INFO, outcome=RECOVERED, message 에 'recovered'") {
            val e = LogEventFactory.createSuspicious(AttackType.ERROR_SPIKE, successful = true)
            e.level shouldBe "INFO"
            e.attributes["outcome"] shouldBe "RECOVERED"
            e.message shouldContain "recovered"
        }
        it("successful=false → level=ERROR, statusCode=500") {
            val e = LogEventFactory.createSuspicious(AttackType.ERROR_SPIKE, successful = false)
            e.level shouldBe "ERROR"
            e.attributes["statusCode"] shouldBe "500"
            e.attributes["outcome"] shouldBe "FAILURE"
        }
    }

    describe("OFF_HOUR_ACCESS 분기") {
        it("successful=true → admin login 성공, level=INFO") {
            val e = LogEventFactory.createSuspicious(AttackType.OFF_HOUR_ACCESS, successful = true)
            e.attributes["outcome"] shouldBe "SUCCESS"
            e.attributes["accountType"] shouldBe "ADMIN"
            e.level shouldBe "INFO"
        }
        it("successful=false → 시도만 실패, level=WARN") {
            val e = LogEventFactory.createSuspicious(AttackType.OFF_HOUR_ACCESS, successful = false)
            e.attributes["outcome"] shouldBe "FAILURE"
            e.level shouldBe "WARN"
        }
    }

    describe("GEO_ANOMALY 분기") {
        it("successful=true → 해외 로그인 성공, level=INFO") {
            val e = LogEventFactory.createSuspicious(AttackType.GEO_ANOMALY, successful = true)
            e.attributes["outcome"] shouldBe "SUCCESS"
            e.level shouldBe "INFO"
        }
        it("successful=false → 해외 로그인 실패, level=WARN") {
            val e = LogEventFactory.createSuspicious(AttackType.GEO_ANOMALY, successful = false)
            e.attributes["outcome"] shouldBe "FAILURE"
            e.level shouldBe "WARN"
        }
    }

    describe("RARE_EVENT 분기") {
        it("successful=true → level=INFO, outcome=RECOVERED") {
            val e = LogEventFactory.createSuspicious(AttackType.RARE_EVENT, successful = true)
            e.level shouldBe "INFO"
            e.attributes["outcome"] shouldBe "RECOVERED"
        }
        it("successful=false → level=ERROR, outcome=FAILURE") {
            val e = LogEventFactory.createSuspicious(AttackType.RARE_EVENT, successful = false)
            e.level shouldBe "ERROR"
            e.attributes["outcome"] shouldBe "FAILURE"
        }
    }
})
