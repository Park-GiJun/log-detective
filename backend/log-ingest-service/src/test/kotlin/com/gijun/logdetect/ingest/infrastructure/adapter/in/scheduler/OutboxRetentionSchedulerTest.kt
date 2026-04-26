package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.scheduler

import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.Clock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Duration
import java.time.Instant

/**
 * OutboxRetentionScheduler 단위 테스트.
 *
 * 주의 — Kotest 6.0.4 + Kotlin 2.3 호환을 위해 다른 테스트와 동일한 스타일을 사용한다.
 *
 * 이슈 #95 — multi-instance 분산 락 (PG advisory lock) 동작 회귀 검증.
 */
class OutboxRetentionSchedulerTest : DescribeSpec({

    val fixedNow: Instant = Instant.parse("2026-04-26T10:00:00Z")
    fun fixedClock(now: Instant = fixedNow) = Clock { now }

    /**
     * 락 획득 성공 케이스의 jdbcTemplate mock.
     */
    fun jdbcMockAcquired(): NamedParameterJdbcTemplate {
        val jdbc = mockk<NamedParameterJdbcTemplate>()
        every {
            jdbc.queryForObject(
                "select pg_try_advisory_xact_lock(:lockId)",
                any<MapSqlParameterSource>(),
                Boolean::class.java,
            )
        } returns true
        return jdbc
    }

    describe("purge — 락 획득 성공") {
        it("PUBLISHED 는 published-days 만큼, DEAD 는 dead-days 만큼 과거 임계값을 계산한다") {
            val port = mockk<OutboxPersistencePort>()
            val scheduler = OutboxRetentionScheduler(
                outboxPersistencePort = port,
                clock = fixedClock(),
                jdbcTemplate = jdbcMockAcquired(),
                publishedDays = 7,
                deadDays = 30,
            )

            val publishedThreshold = slot<Instant>()
            val deadThreshold = slot<Instant>()
            every { port.purgePublishedOlderThan(capture(publishedThreshold)) } returns 0
            every { port.purgeDeadOlderThan(capture(deadThreshold)) } returns 0

            scheduler.purge()

            // Clock 이 고정되어 있으므로 결정적으로 검증 가능.
            publishedThreshold.captured shouldBe fixedNow.minus(Duration.ofDays(7))
            deadThreshold.captured shouldBe fixedNow.minus(Duration.ofDays(30))
        }

        it("port 의 두 purge 메서드가 정확히 1회씩 호출된다") {
            val port = mockk<OutboxPersistencePort>()
            val scheduler = OutboxRetentionScheduler(
                outboxPersistencePort = port,
                clock = fixedClock(),
                jdbcTemplate = jdbcMockAcquired(),
                publishedDays = 7,
                deadDays = 30,
            )
            every { port.purgePublishedOlderThan(any()) } returns 5
            every { port.purgeDeadOlderThan(any()) } returns 2

            scheduler.purge()

            verify(exactly = 1) { port.purgePublishedOlderThan(any()) }
            verify(exactly = 1) { port.purgeDeadOlderThan(any()) }
        }

        it("커스텀 days 설정도 그대로 반영된다") {
            val port = mockk<OutboxPersistencePort>()
            val scheduler = OutboxRetentionScheduler(
                outboxPersistencePort = port,
                clock = fixedClock(),
                jdbcTemplate = jdbcMockAcquired(),
                publishedDays = 1,
                deadDays = 90,
            )

            val publishedThreshold = slot<Instant>()
            val deadThreshold = slot<Instant>()
            every { port.purgePublishedOlderThan(capture(publishedThreshold)) } returns 0
            every { port.purgeDeadOlderThan(capture(deadThreshold)) } returns 0

            scheduler.purge()

            publishedThreshold.captured shouldBe fixedNow.minus(Duration.ofDays(1))
            deadThreshold.captured shouldBe fixedNow.minus(Duration.ofDays(90))
        }
    }

    describe("purge — 분산 락 (이슈 #95)") {

        it("락 획득 실패 시 purge 메서드를 호출하지 않는다 — multi-instance 중복 실행 차단") {
            val port = mockk<OutboxPersistencePort>(relaxed = true)
            val jdbc = mockk<NamedParameterJdbcTemplate>()
            every {
                jdbc.queryForObject(
                    any<String>(),
                    any<MapSqlParameterSource>(),
                    Boolean::class.java,
                )
            } returns false

            val scheduler = OutboxRetentionScheduler(
                outboxPersistencePort = port,
                clock = fixedClock(),
                jdbcTemplate = jdbc,
                publishedDays = 7,
                deadDays = 30,
            )

            scheduler.purge()

            verify(exactly = 0) { port.purgePublishedOlderThan(any()) }
            verify(exactly = 0) { port.purgeDeadOlderThan(any()) }
        }

        it("락 결과가 null 이어도 안전하게 skip 한다 — purge 미호출") {
            val port = mockk<OutboxPersistencePort>(relaxed = true)
            val jdbc = mockk<NamedParameterJdbcTemplate>()
            every {
                jdbc.queryForObject(
                    any<String>(),
                    any<MapSqlParameterSource>(),
                    Boolean::class.java,
                )
            } returns null

            val scheduler = OutboxRetentionScheduler(
                outboxPersistencePort = port,
                clock = fixedClock(),
                jdbcTemplate = jdbc,
                publishedDays = 7,
                deadDays = 30,
            )

            scheduler.purge()

            verify(exactly = 0) { port.purgePublishedOlderThan(any()) }
            verify(exactly = 0) { port.purgeDeadOlderThan(any()) }
        }

        it("RETENTION_LOCK_ID 상수로 advisory lock 을 시도한다 — lockId 파라미터 검증") {
            val port = mockk<OutboxPersistencePort>(relaxed = true)
            val jdbc = mockk<NamedParameterJdbcTemplate>()
            val captured = slot<MapSqlParameterSource>()
            every {
                jdbc.queryForObject(
                    "select pg_try_advisory_xact_lock(:lockId)",
                    capture(captured),
                    Boolean::class.java,
                )
            } returns true

            val scheduler = OutboxRetentionScheduler(
                outboxPersistencePort = port,
                clock = fixedClock(),
                jdbcTemplate = jdbc,
                publishedDays = 7,
                deadDays = 30,
            )

            scheduler.purge()

            captured.captured.getValue("lockId") shouldBe OutboxRetentionScheduler.RETENTION_LOCK_ID
        }
    }
})
