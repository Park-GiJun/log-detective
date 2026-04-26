package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.adapter

import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.repository.OutboxJpaRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

/**
 * OutboxPersistenceAdapter 단위 테스트.
 *
 * 검증 대상 — `fetchPending(limit)` 의 외부 입력 클램프 가드 (이슈 #63).
 *
 * 주의 — Kotest 6.1.0 + Kotlin 2.3 호환성 이슈로 빌드 시 자동 실행되지 않을 수 있다.
 * IDE 에서 개별 실행하여 검증한다.
 */
class OutboxPersistenceAdapterTest : DescribeSpec({

    describe("fetchPending limit 클램프") {

        it("limit 가 1 미만이면 1 로 보정된다 — repository 에 1 이 전달된다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo)
            val captured = slot<Int>()
            every { repo.fetchPendingForUpdate(capture(captured)) } returns emptyList()

            adapter.fetchPending(0)
            captured.captured shouldBe 1

            adapter.fetchPending(-100)
            captured.captured shouldBe 1
        }

        it("limit 가 MAX_LIMIT(1000) 초과면 1000 으로 잘린다 — DoS 방어") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo)
            val captured = slot<Int>()
            every { repo.fetchPendingForUpdate(capture(captured)) } returns emptyList()

            adapter.fetchPending(Int.MAX_VALUE)
            captured.captured shouldBe 1000

            adapter.fetchPending(10_000)
            captured.captured shouldBe 1000
        }

        it("정상 범위(1..1000)는 그대로 통과한다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo)
            val captured = slot<Int>()
            every { repo.fetchPendingForUpdate(capture(captured)) } returns emptyList()

            adapter.fetchPending(1)
            captured.captured shouldBe 1

            adapter.fetchPending(500)
            captured.captured shouldBe 500

            adapter.fetchPending(1000)
            captured.captured shouldBe 1000
        }

        it("repository 호출은 정확히 1회씩만 일어난다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo)
            every { repo.fetchPendingForUpdate(any()) } returns emptyList()

            adapter.fetchPending(50)
            verify(exactly = 1) { repo.fetchPendingForUpdate(50) }
        }
    }
})
