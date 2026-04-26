package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.adapter

import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort.DeadUpdate
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort.FailureUpdate
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.repository.OutboxJpaRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant

/**
 * OutboxPersistenceAdapter 단위 테스트.
 *
 * 검증 대상:
 * - `fetchPending(limit)` 의 외부 입력 클램프 가드 (이슈 #63)
 * - `markFailedAll` / `markDeadAll` 의 (error, nextAttemptAt) 그룹별 IN-list UPDATE (이슈 #89)
 */
class OutboxPersistenceAdapterTest : DescribeSpec({

    describe("fetchPending limit 클램프") {

        it("limit 가 1 미만이면 1 로 보정된다 — repository 에 1 이 전달된다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { java.time.Instant.now() })
            val captured = slot<Int>()
            every { repo.fetchPendingForUpdate(capture(captured)) } returns emptyList()

            adapter.fetchPending(0)
            captured.captured shouldBe 1

            adapter.fetchPending(-100)
            captured.captured shouldBe 1
        }

        it("limit 가 MAX_LIMIT(1000) 초과면 1000 으로 잘린다 — DoS 방어") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { java.time.Instant.now() })
            val captured = slot<Int>()
            every { repo.fetchPendingForUpdate(capture(captured)) } returns emptyList()

            adapter.fetchPending(Int.MAX_VALUE)
            captured.captured shouldBe 1000

            adapter.fetchPending(10_000)
            captured.captured shouldBe 1000
        }

        it("정상 범위(1..1000)는 그대로 통과한다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { java.time.Instant.now() })
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
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { java.time.Instant.now() })
            every { repo.fetchPendingForUpdate(any()) } returns emptyList()

            adapter.fetchPending(50)
            verify(exactly = 1) { repo.fetchPendingForUpdate(50) }
        }
    }

    describe("markFailedAll — (error, nextAttemptAt) 그룹별 IN-list UPDATE (이슈 #89)") {

        it("동일 (error, nextAttemptAt) 100 건은 markFailedBatch 1회로 묶인다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { Instant.now() })
            val nextAttemptAt = Instant.parse("2026-04-26T10:00:00Z")
            val failures = (1L..100L).map { FailureUpdate(it, "ES down", nextAttemptAt) }

            val idsSlot = slot<List<Long>>()
            every { repo.markFailedBatch(capture(idsSlot), any(), any()) } returns 100

            adapter.markFailedAll(failures)

            verify(exactly = 1) { repo.markFailedBatch(any(), "ES down", nextAttemptAt) }
            idsSlot.captured shouldContainExactlyInAnyOrder (1L..100L).toList()
        }

        it("error 가 다르면 그룹별로 호출이 나뉜다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { Instant.now() })
            val t1 = Instant.parse("2026-04-26T10:00:10Z")
            val t2 = Instant.parse("2026-04-26T10:00:20Z")
            val failures = listOf(
                FailureUpdate(1L, "ES down", t1),
                FailureUpdate(2L, "ES down", t1),
                FailureUpdate(3L, "kafka timeout", t2),
            )
            every { repo.markFailedBatch(any(), any(), any()) } returns 0

            adapter.markFailedAll(failures)

            verify(exactly = 1) { repo.markFailedBatch(match { it.containsAll(listOf(1L, 2L)) && it.size == 2 }, "ES down", t1) }
            verify(exactly = 1) { repo.markFailedBatch(listOf(3L), "kafka timeout", t2) }
        }

        it("입력이 비어있으면 repository 를 호출하지 않는다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { Instant.now() })

            adapter.markFailedAll(emptyList())

            verify(exactly = 0) { repo.markFailedBatch(any(), any(), any()) }
        }
    }

    describe("markDeadAll — error 그룹별 IN-list UPDATE (이슈 #89)") {

        it("동일 error 50 건은 markDeadBatch 1회로 묶인다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { Instant.now() })
            val deads = (1L..50L).map { DeadUpdate(it, "max attempts exceeded") }

            val idsSlot = slot<List<Long>>()
            every { repo.markDeadBatch(capture(idsSlot), any()) } returns 50

            adapter.markDeadAll(deads)

            verify(exactly = 1) { repo.markDeadBatch(any(), "max attempts exceeded") }
            idsSlot.captured shouldContainExactlyInAnyOrder (1L..50L).toList()
        }

        it("error 가 다르면 그룹별로 분리되어 호출된다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { Instant.now() })
            val deads = listOf(
                DeadUpdate(1L, "unsupported channel: FILE"),
                DeadUpdate(2L, "unsupported channel: FILE"),
                DeadUpdate(3L, "max attempts exceeded"),
            )
            every { repo.markDeadBatch(any(), any()) } returns 0

            adapter.markDeadAll(deads)

            verify(exactly = 1) { repo.markDeadBatch(match { it.containsAll(listOf(1L, 2L)) && it.size == 2 }, "unsupported channel: FILE") }
            verify(exactly = 1) { repo.markDeadBatch(listOf(3L), "max attempts exceeded") }
        }

        it("입력이 비어있으면 repository 를 호출하지 않는다") {
            val repo = mockk<OutboxJpaRepository>()
            val adapter = OutboxPersistenceAdapter(repo, com.gijun.logdetect.ingest.domain.port.Clock { Instant.now() })

            adapter.markDeadAll(emptyList())

            verify(exactly = 0) { repo.markDeadBatch(any(), any()) }
        }
    }
})
