package com.gijun.logdetect.ingest.integration

import com.gijun.logdetect.ingest.application.port.`in`.command.DispatchOutboxUseCase
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.enums.OutboxStatus
import com.gijun.logdetect.ingest.domain.model.Outbox
import com.gijun.logdetect.ingest.domain.port.Clock
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.repository.OutboxJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.Import
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 회귀 방지 — `DispatchOutboxHandler` 의 외부 IO (ES `_bulk` / Kafka `publishBulk`) 가
 * DB 트랜잭션 밖에서 실행되는지 검증한다 (이슈 #25 / #36 / #60).
 *
 * WHY — 과거 `OutboxPublisher.pollAndDispatch` 가 클래스 단위 `@Transactional` 로 묶여 있어,
 * 1회 폴링 = 1 트랜잭션 안에서 외부 IO 가 발생했다. ES/Kafka 가 느리면 그 시간만큼 DB 커넥션 + 행 락이
 * 점유되어 (1) 커넥션 풀 고갈, (2) 다른 dispatch 인스턴스 starvation 이 일어났다.
 *
 * PR #83 으로 트랜잭션이 분해되었으나, 회귀(반복 실수 패턴 #3)를 막을 자동 검증이 없다.
 * 본 테스트는 다음 두 신호로 회귀를 검출한다:
 *
 * 1. **커넥션 풀 점유 신호** — HikariCP `maximum-pool-size=1` 로 강제. 외부 IO 가 트랜잭션 안이면
 *    dispatch 가 1초간 1개 커넥션을 holding 하므로, 다른 thread 의 DB 작업이 그만큼 지연된다.
 * 2. **시간 측정** — dispatch 가 외부 IO 진행 중인 시점에 다른 thread 가 outbox 테이블 INSERT 시도.
 *    트랜잭션 분해가 정상이면 다른 thread 의 작업은 즉시 (≪ 1초) 완료된다.
 *
 * 외부 IO 지연은 [DelayingTestPorts] 가 mock 으로 1초 sleep 후 success 를 반환하여 시뮬레이션.
 */
@Import(LockHoldingRegressionTest.DelayingTestPorts::class)
@TestPropertySource(
    properties = [
        // 핵심 — 풀 1개로 좁혀 트랜잭션 안의 외부 IO 가 다른 thread 의 DB 접근을 막는지 노출.
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.connection-timeout=2000",
        // 본 테스트는 dispatch 를 직접 트리거하므로 자동 폴링이 끼어들지 않게 길게 잡는다.
        "logdetect.outbox.poll-interval-ms=600000",
    ],
)
class LockHoldingRegressionTest : IntegrationTestBase() {

    @Autowired
    private lateinit var dispatchOutboxUseCase: DispatchOutboxUseCase

    @Autowired
    private lateinit var outboxPersistencePort: OutboxPersistencePort

    @Autowired
    private lateinit var outboxRepository: OutboxJpaRepository

    @Autowired
    private lateinit var clock: Clock

    @BeforeEach
    fun cleanupOutbox() {
        outboxRepository.deleteAll()
        DelayingTestPorts.reset()
    }

    @Test
    fun `dispatchPending 의 외부 IO 가 DB 트랜잭션 밖에서 실행되어 다른 커넥션을 차단하지 않는다`() {
        // given — PENDING outbox 행 2개 (ES + KAFKA) 를 사전 INSERT.
        val payload = """{"k":"v"}"""
        val rows = listOf(
            Outbox.newPending(
                clock = clock,
                aggregateId = UUID.randomUUID().toString(),
                channel = ChannelType.ES,
                destination = "logs-2026.04.26",
                payload = payload,
            ),
            Outbox.newPending(
                clock = clock,
                aggregateId = UUID.randomUUID().toString(),
                channel = ChannelType.KAFKA,
                destination = "logs.raw",
                payload = payload,
            ),
        )
        outboxPersistencePort.saveAll(rows)
        assertThat(outboxRepository.count()).isEqualTo(2)

        // when — Thread A: dispatchPending 비동기 호출. 외부 IO 가 1초 지연.
        val dispatchFuture = CompletableFuture.supplyAsync {
            dispatchOutboxUseCase.dispatchPending()
        }

        // 외부 IO 가 시작될 시간 확보 — fetchPending(REQUIRES_NEW) 트랜잭션이 이미 커밋된 시점.
        // 회귀 시 dispatch 트랜잭션이 외부 IO 1초 동안 풀의 유일한 커넥션을 holding 한다.
        DelayingTestPorts.awaitExternalIoStart(timeoutMs = 3_000)

        // then — Thread B: 다른 커넥션이 즉시 확보되어 outbox 테이블에 INSERT 가능해야 한다.
        // 회귀(트랜잭션 안의 외부 IO) 가 들어오면 풀 고갈로 connection-timeout(2s) 까지 차단된다.
        val newRow = Outbox.newPending(
            clock = clock,
            aggregateId = UUID.randomUUID().toString(),
            channel = ChannelType.ES,
            destination = "logs-2026.04.26",
            payload = payload,
        )
        val before = System.currentTimeMillis()
        outboxPersistencePort.saveAll(listOf(newRow))
        val elapsed = System.currentTimeMillis() - before

        // 트랜잭션 분해가 정상이면 dispatch 의 외부 IO 1초가 끝나기 전이라도 INSERT 가 즉시 성공.
        // 임계값 500ms 는 GC / Hikari 첫 connection 획득 노이즈를 흡수하기 위한 여유.
        assertThat(elapsed)
            .withFailMessage(
                "Outbox INSERT 가 %d ms 걸렸음 — dispatch 트랜잭션이 외부 IO 동안 커넥션을 holding 한 회귀 가능성. " +
                    "DispatchOutboxHandler.dispatchPending() 의 외부 port 호출이 @Transactional 안으로 들어왔는지 확인하라.",
                elapsed,
            )
            .isLessThan(500L)

        // dispatch 정상 완료 검증 (PUBLISHED 전이까지 동일 트랜잭션이 아닌지 추가 확인).
        val summary = dispatchFuture.get(10, TimeUnit.SECONDS)
        assertThat(summary.total).isEqualTo(2)
        assertThat(summary.succeeded).isEqualTo(2)

        val finalRows = outboxRepository.findAll()
        // dispatch 가 잡았던 2건은 PUBLISHED, 사후 INSERT 한 1건은 PENDING.
        assertThat(finalRows).hasSize(3)
        val statuses = finalRows.groupingBy { it.status }.eachCount()
        assertThat(statuses[OutboxStatus.PUBLISHED]).isEqualTo(2)
        assertThat(statuses[OutboxStatus.PENDING]).isEqualTo(1)
    }

    /**
     * 외부 IO 지연을 시뮬레이션하는 [LogEventSearchPort] / [LogEventMessagePort] 대체 구현.
     *
     * @TestConfiguration + @Primary 로 운영 어댑터 ([com.gijun.logdetect.ingest.infrastructure.adapter.out.search.LogEventSearchAdapter] /
     * [com.gijun.logdetect.ingest.infrastructure.adapter.out.messaging.LogEventMessageAdapter]) 를 가린다.
     * 운영 어댑터는 그대로 컨텍스트에 남아있지만 `@Primary` 마커로 핸들러가 본 빈을 주입받는다.
     *
     * `awaitExternalIoStart` 는 외부 IO 가 시작된 시점(=fetchPending 트랜잭션이 이미 커밋된 시점)을 동기화하기 위한 latch.
     */
    @TestConfiguration
    class DelayingTestPorts {

        @Bean
        @Primary
        fun delayingSearchPort(): LogEventSearchPort = object : LogEventSearchPort {
            override fun indexBulk(
                documents: List<LogEventSearchPort.SearchDocument>,
            ): LogEventSearchPort.BulkResult {
                signalIoStart()
                Thread.sleep(EXTERNAL_IO_DELAY_MS)
                return LogEventSearchPort.BulkResult(
                    successIds = documents.map { it.id }.toSet(),
                    failures = emptyMap(),
                )
            }
        }

        @Bean
        @Primary
        fun delayingMessagePort(): LogEventMessagePort = object : LogEventMessagePort {
            override fun publishBulk(
                messages: List<LogEventMessagePort.KafkaMessage>,
            ): LogEventMessagePort.BulkResult {
                signalIoStart()
                Thread.sleep(EXTERNAL_IO_DELAY_MS)
                return LogEventMessagePort.BulkResult(
                    successKeys = messages.map { it.key }.toSet(),
                    failures = emptyMap(),
                )
            }
        }

        companion object {
            private const val EXTERNAL_IO_DELAY_MS = 1_000L

            // 두 채널 (ES / KAFKA) 중 어느 쪽이 먼저 호출돼도 latch 한 번 release 하면 OK.
            @Volatile
            private var ioStartedAt: Long = -1L
            private val ioStartedLock = Object()

            fun signalIoStart() {
                synchronized(ioStartedLock) {
                    if (ioStartedAt < 0) ioStartedAt = System.currentTimeMillis()
                    ioStartedLock.notifyAll()
                }
            }

            fun awaitExternalIoStart(timeoutMs: Long) {
                synchronized(ioStartedLock) {
                    val deadline = System.currentTimeMillis() + timeoutMs
                    while (ioStartedAt < 0) {
                        val remaining = deadline - System.currentTimeMillis()
                        check(remaining > 0) { "외부 IO 가 ${timeoutMs}ms 안에 시작되지 않음 — dispatch 가 동작하지 않음" }
                        ioStartedLock.wait(remaining)
                    }
                }
            }

            fun reset() {
                synchronized(ioStartedLock) {
                    ioStartedAt = -1L
                }
            }
        }
    }
}
