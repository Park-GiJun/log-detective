package com.gijun.logdetect.ingest.infrastructure.adapter.out.messaging

import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort.BulkResult
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort.KafkaMessage
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Outbox dispatch 전용 Kafka 어댑터 — raw ByteArray payload 를 batch 로 발행.
 *
 * WHY — 기존 forEach 단건 send + 동기 wait 는 RTT × N 의 직렬 비용. send 는 우선 모두 호출하여
 * producer batch 에 모이게 한 뒤, 마지막에 한 번에 flush + 결과를 join 한다.
 * 같은 토픽으로 묶일 때 linger.ms / batch.size 가 producer 단에서 자연스럽게 묶어준다 (이슈 #40).
 *
 * 부분 실패 처리 — 예외가 난 future 만 failures 에 담고 나머지는 successKeys 로 반환.
 * Outbox 단에서 성공한 행만 markPublished, 실패는 markFailed/Dead 로 분리한다.
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

        // 1) 먼저 모든 send 를 호출 — producer 가 batch 에 누적.
        val pending: List<Pair<KafkaMessage, CompletableFuture<*>>> = messages.map { msg ->
            msg to outboxKafkaTemplate.send(msg.topic, msg.key, msg.payload)
        }

        // 2) flush — 누적된 batch 를 즉시 broker 로 push.
        outboxKafkaTemplate.flush()

        // 3) 결과 join — broker ack 까지 대기. 부분 실패는 failures 에 담는다.
        val failures = mutableMapOf<String, String>()
        pending.forEach { (msg, future) ->
            try {
                future.get(SEND_TIMEOUT_SEC, TimeUnit.SECONDS)
            } catch (e: Exception) {
                failures[msg.key] = e.message ?: e.javaClass.simpleName
            }
        }

        if (failures.isNotEmpty()) {
            logger.warn("Kafka bulk 부분 실패 — 총 {} 건 중 {} 건 실패", messages.size, failures.size)
        } else {
            logger.debug("Kafka bulk 발행 완료 — {} 건", messages.size)
        }

        val successKeys = messages.asSequence()
            .map { it.key }
            .filter { it !in failures }
            .toSet()
        return BulkResult(successKeys = successKeys, failures = failures)
    }

    companion object {
        private const val SEND_TIMEOUT_SEC = 30L
    }
}
