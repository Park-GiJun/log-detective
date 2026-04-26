package com.gijun.logdetect.ingest.domain.model

import com.gijun.logdetect.common.domain.enums.Severity
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class IngestResultTest : DescribeSpec({

    fun build(riskScore: Double) = IngestResult(
        ingestId = 1L,
        transactionId = "tx",
        riskLevel = Severity.LOW,
        riskScore = riskScore,
        triggeredRules = emptyList(),
        detectedAt = Instant.parse("2026-04-26T10:00:00Z"),
    )

    describe("riskScore 경계값 — 정상 범위") {
        it("0.0 (하한) 은 허용된다") {
            build(0.0).riskScore shouldBe 0.0
        }

        it("100.0 (상한) 은 허용된다") {
            build(100.0).riskScore shouldBe 100.0
        }

        it("범위 내 임의 값 (50.5) 은 허용된다") {
            build(50.5).riskScore shouldBe 50.5
        }
    }

    describe("riskScore 경계값 — 비정상 범위") {
        it("음수 (-0.0001) 은 IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { build(-0.0001) }
        }

        it("100 초과 (100.0001) 는 IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { build(100.0001) }
        }
    }

    describe("riskScore 특이값 — NaN / Infinity") {
        it("NaN 은 IllegalArgumentException — 비교 연산이 항상 false 이므로 in 0.0..100.0 검증 실패") {
            shouldThrow<IllegalArgumentException> { build(Double.NaN) }
        }

        it("POSITIVE_INFINITY 는 IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { build(Double.POSITIVE_INFINITY) }
        }

        it("NEGATIVE_INFINITY 는 IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { build(Double.NEGATIVE_INFINITY) }
        }
    }
})
