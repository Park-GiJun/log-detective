package com.gijun.logdetect.ingest.application.handler.command

import com.gijun.logdetect.ingest.application.dto.result.DispatchSummary
import com.gijun.logdetect.ingest.application.port.`in`.command.DispatchOutboxUseCase
import com.gijun.logdetect.ingest.application.port.out.ErrorRedactorPort
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort.DeadUpdate
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort.FailureUpdate
import com.gijun.logdetect.ingest.domain.Clock
import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.model.Outbox
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * Outbox 발행 정책 보유자.
 *
 * 책임 (이슈 #44):
 * - PENDING 행을 batch 로 가져오기 (짧은 트랜잭션).
 * - 채널별 그룹화 후 일괄 dispatch (#40 — ES `_bulk` + Kafka batch).
 * - 결과를 mark* 일괄 처리 (짧은 트랜잭션).
 * - 백오프 / MAX_ATTEMPTS / 미지원 채널 즉시 dead 정책.
 *
 * 트랜잭션 분해 (이슈 #25):
 * 1) fetchPending — REQUIRES_NEW (락 + 빠른 커밋)
 * 2) ES bulk + Kafka bulk — 트랜잭션 밖. 외부 IO 가 트랜잭션 길이를 늘리지 않음.
 * 3) markPublishedAll / markFailedAll / markDeadAll — REQUIRES_NEW.
 *
 * @param baseBackoffSec 백오프 base (5s × 2^n).
 * @param maxAttempts MAX_ATTEMPTS — 도달 시 markDead.
 * @param maxBackoffShift 백오프 상한 — 5s × 2^6 = 320s ≈ 5분.
 * @param maxErrorLength last_error 컬럼 길이 보호.
 */
open class DispatchOutboxHandler(
    private val outboxPersistencePort: OutboxPersistencePort,
    private val logEventSearchPort: LogEventSearchPort,
    private val logEventMessagePort: LogEventMessagePort,
    private val clock: Clock,
    private val errorRedactor: ErrorRedactorPort,
    private val dispatchExecutor: Executor = Runnable::run,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val baseBackoffSec: Long = DEFAULT_BASE_BACKOFF_SEC,
    private val maxBackoffShift: Int = DEFAULT_MAX_BACKOFF_SHIFT,
    private val maxErrorLength: Int = DEFAULT_MAX_ERROR_LENGTH,
) : DispatchOutboxUseCase {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun dispatchPending(): DispatchSummary {
        // 1) 짧은 트랜잭션 — fetch + 커밋. 외부 IO 가 락을 점유하지 않게.
        val pending = outboxPersistencePort.fetchPending(batchSize)
        if (pending.isEmpty()) return DispatchSummary.EMPTY

        // 2) 미지원 채널은 즉시 dead — 외부 IO 호출 없이 부 자료구조에 분류.
        val (unsupported, supported) = pending.partition { it.channel !in SUPPORTED_CHANNELS }

        val unsupportedDeads: List<DeadUpdate> = unsupported.mapNotNull { row ->
            row.id?.let { DeadUpdate(it, "unsupported channel: ${row.channel}") }
        }

        // 3) 채널별 그룹화 후 일괄 발행 (이슈 #40). 두 채널은 서로 독립이므로 병렬 호출 (이슈 #93).
        val byChannel: Map<ChannelType, List<Outbox>> = supported.groupBy { it.channel }
        val esRows = byChannel[ChannelType.ES] ?: emptyList()
        val kafkaRows = byChannel[ChannelType.KAFKA] ?: emptyList()

        val esFuture: CompletableFuture<ChannelOutcome> =
            CompletableFuture.supplyAsync({ dispatchEs(esRows) }, dispatchExecutor)
        val kafkaFuture: CompletableFuture<ChannelOutcome> =
            CompletableFuture.supplyAsync({ dispatchKafka(kafkaRows) }, dispatchExecutor)

        // 두 future 의 join — 어느 한 쪽이 늦어도 다른 쪽이 먼저 끝나 있다.
        val esOutcome = esFuture.join()
        val kafkaOutcome = kafkaFuture.join()

        val publishedIds = esOutcome.publishedIds + kafkaOutcome.publishedIds
        val failures = esOutcome.failures + kafkaOutcome.failures
        val deads = unsupportedDeads + esOutcome.deads + kafkaOutcome.deads

        // 4) 짧은 트랜잭션 — 결과 일괄 반영.
        if (publishedIds.isNotEmpty()) outboxPersistencePort.markPublishedAll(publishedIds)
        if (failures.isNotEmpty()) outboxPersistencePort.markFailedAll(failures)
        if (deads.isNotEmpty()) outboxPersistencePort.markDeadAll(deads)

        val summary = DispatchSummary(
            total = pending.size,
            succeeded = publishedIds.size,
            failed = failures.size,
            dead = deads.size,
        )
        logger.debug(
            "Outbox dispatch — total: {}, success: {}, failed: {}, dead: {}",
            summary.total,
            summary.succeeded,
            summary.failed,
            summary.dead,
        )
        return summary
    }

    private fun dispatchEs(rows: List<Outbox>): ChannelOutcome {
        if (rows.isEmpty()) return ChannelOutcome.EMPTY
        // ES bulk 호출은 한 번. eventId(=aggregateId) 를 ES doc id 로 사용해 멱등성 확보.
        val docs = rows.mapNotNull { row ->
            row.id ?: return@mapNotNull null
            LogEventSearchPort.SearchDocument(
                index = row.destination,
                id = row.aggregateId,
                payload = row.payload.toByteArray(Charsets.UTF_8),
            )
        }
        val byDocId: Map<String, Outbox> = rows.associateBy { it.aggregateId }
        val publishedIds = mutableListOf<Long>()
        val failures = mutableListOf<FailureUpdate>()
        val deads = mutableListOf<DeadUpdate>()

        val result = try {
            logEventSearchPort.indexBulk(docs)
        } catch (e: Exception) {
            classifyAllAsFailed(rows, e.message ?: e.javaClass.simpleName, failures, deads)
            return ChannelOutcome(publishedIds, failures, deads)
        }

        result.successIds.forEach { docId ->
            byDocId[docId]?.id?.let { publishedIds += it }
        }
        result.failures.forEach { (docId, err) ->
            byDocId[docId]?.let { row -> classifyFailure(row, err, failures, deads) }
        }
        return ChannelOutcome(publishedIds, failures, deads)
    }

    private fun dispatchKafka(rows: List<Outbox>): ChannelOutcome {
        if (rows.isEmpty()) return ChannelOutcome.EMPTY
        val messages = rows.mapNotNull { row ->
            row.id ?: return@mapNotNull null
            LogEventMessagePort.KafkaMessage(
                topic = row.destination,
                key = row.aggregateId,
                payload = row.payload.toByteArray(Charsets.UTF_8),
            )
        }
        val byKey: Map<String, Outbox> = rows.associateBy { it.aggregateId }
        val publishedIds = mutableListOf<Long>()
        val failures = mutableListOf<FailureUpdate>()
        val deads = mutableListOf<DeadUpdate>()

        val result = try {
            logEventMessagePort.publishBulk(messages)
        } catch (e: Exception) {
            classifyAllAsFailed(rows, e.message ?: e.javaClass.simpleName, failures, deads)
            return ChannelOutcome(publishedIds, failures, deads)
        }

        result.successKeys.forEach { key ->
            byKey[key]?.id?.let { publishedIds += it }
        }
        result.failures.forEach { (key, err) ->
            byKey[key]?.let { row -> classifyFailure(row, err, failures, deads) }
        }
        return ChannelOutcome(publishedIds, failures, deads)
    }

    private fun classifyAllAsFailed(
        rows: List<Outbox>,
        rawError: String,
        failures: MutableList<FailureUpdate>,
        deads: MutableList<DeadUpdate>,
    ) {
        rows.forEach { classifyFailure(it, rawError, failures, deads) }
    }

    /**
     * 실패한 한 행을 백오프/MAX_ATTEMPTS 정책에 따라 [failures] 또는 [deads] 로 분류.
     */
    private fun classifyFailure(
        outbox: Outbox,
        rawError: String,
        failures: MutableList<FailureUpdate>,
        deads: MutableList<DeadUpdate>,
    ) {
        val id = outbox.id ?: return
        val error = errorRedactor.redact(rawError).take(maxErrorLength)
        val nextAttempts = outbox.attempts + 1
        if (nextAttempts >= maxAttempts) {
            logger.warn("Outbox 최대 시도 초과 — id: {}, attempts: {}", id, nextAttempts)
            deads += DeadUpdate(id, error)
            return
        }
        val backoffSec = baseBackoffSec shl nextAttempts.coerceAtMost(maxBackoffShift)
        failures += FailureUpdate(id, error, clock.now().plusSeconds(backoffSec))
    }

    /**
     * 채널별 dispatch 결과 — 병렬 실행 후 main 스레드에서 합치기 위한 immutable carrier.
     * 호출자별 분리된 mutable list 를 사용하므로 thread-safe.
     */
    private data class ChannelOutcome(
        val publishedIds: List<Long>,
        val failures: List<FailureUpdate>,
        val deads: List<DeadUpdate>,
    ) {
        companion object {
            val EMPTY = ChannelOutcome(emptyList(), emptyList(), emptyList())
        }
    }

    companion object {
        private const val DEFAULT_BATCH_SIZE = 100
        private const val DEFAULT_MAX_ATTEMPTS = 5
        // 백오프: nextAttempt(1..5) → 5s × 2^n.
        private const val DEFAULT_BASE_BACKOFF_SEC = 5L
        private const val DEFAULT_MAX_BACKOFF_SHIFT = 6
        private const val DEFAULT_MAX_ERROR_LENGTH = 1000
        private val SUPPORTED_CHANNELS = setOf(ChannelType.ES, ChannelType.KAFKA)
    }
}
