package com.gijun.logdetect.ingest.infrastructure.adapter.out.messaging

import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort.BulkResult
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort.KafkaMessage
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Outbox dispatch 전용 Kafka 어댑터 — raw ByteArray payload 를 batch 로 발행.
 *
 * WHY — 기존 구현은 send 후 forEach 안에서 `future.get(30s)` 를 호출하여, 한 행이라도 broker ack
 * 가 늦으면 그 행의 timeout 만큼 다음 행이 직렬로 대기했다 (worst-case 30s × N).
 * 이슈 #90 — 모든 future 를 먼저 등록하고 `CompletableFuture.allOf(...).get(timeout)` 으로
 * **단일 timeout 창 안에서 일괄 wait**. 정상 흐름이면 가장 느린 한 건의 시간으로 끝난다.
 *
 * 부분 실패 처리 — `allOf` 가 timeout 으로 깨지더라도 개별 future 의 isDone/isCompletedExceptionally
 * 를 검사하여 완료된 건은 성공/실패 분류, 미완료 건은 timeout 사유로 failures 에 누적한다.
 * 같은 토픽으로 묶일 때 linger.ms / batch.size 가 producer 단에서 자연스럽게 묶어준다 (이슈 #40).
 */
@Component
class LogEventMessageAdapter(
    private val outboxKafkaTemplate: KafkaTemplate<String, ByteArray>,
) : LogEventMessagePort {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun publishBulk(messages: List<KafkaMessage>): BulkResult {
        if (messages.isEmpty()) {
            return BulkResult(successKeys = emptySet(), failures = emptyMap())
        }

        // 1) 모든 send 를 먼저 호출 — producer 의 linger.ms / batch.size 에 누적.
        val pending: List<Pair<KafkaMessage, CompletableFuture<*>>> = messages.map { msg ->
            msg to outboxKafkaTemplate.send(msg.topic, msg.key, msg.payload)
        }

        // 2) flush — 누적된 batch 를 즉시 broker 로 push.
        outboxKafkaTemplate.flush()

        // 3) 단일 timeout 창에서 일괄 wait. 어느 future 가 이미 예외든 정상이든 allOf 가 묶어 준다.
        //    allOf 의 결과만으로는 부분 실패를 식별할 수 없으므로, 개별 future 를 다시 순회한다.
        val combined = CompletableFuture.allOf(*pending.map { it.second }.toTypedArray())
        val timedOut = try {
            combined.get(SEND_TIMEOUT_SEC, TimeUnit.SECONDS)
            false
        } catch (_: TimeoutException) {
            true
        } catch (_: Exception) {
            // 개별 future 결과는 아래 루프에서 다시 검사하므로 여기서는 흡수.
            false
        }

        val failures = mutableMapOf<String, String>()
        pending.forEach { (msg, future) ->
            when {
                future.isCompletedExceptionally -> failures[msg.key] = extractError(future)
                !future.isDone -> failures[msg.key] = "send timeout (${SEND_TIMEOUT_SEC}s)"
                // isDone && !isCompletedExceptionally → 성공
            }
        }

        if (failures.isNotEmpty()) {
            logger.warn(
                "Kafka bulk 부분 실패 — 총 {} 건 중 {} 건 실패 (timeout={})",
                messages.size,
                failures.size,
                timedOut,
            )
        } else {
            logger.debug("Kafka bulk 발행 완료 — {} 건", messages.size)
        }

        val successKeys = messages.asSequence()
            .map { it.key }
            .filter { it !in failures }
            .toSet()
        return BulkResult(successKeys = successKeys, failures = failures)
    }

    /**
     * 이미 isCompletedExceptionally 인 future 의 원인 메시지를 추출.
     * `get()` 은 ExecutionException 으로 감싸므로 cause 까지 파고든다.
     */
    private fun extractError(future: CompletableFuture<*>): String =
        try {
            future.get(0, TimeUnit.MILLISECONDS)
            "unknown error"
        } catch (e: Exception) {
            val cause = e.cause ?: e
            cause.message ?: cause.javaClass.simpleName
        }

    companion object {
        private const val SEND_TIMEOUT_SEC = 30L
    }
}
