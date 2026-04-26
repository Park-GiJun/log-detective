package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.ingest.domain.model.Outbox
import java.time.Instant

interface OutboxPersistencePort {
    fun saveAll(outboxes: List<Outbox>)

    /**
     * `FOR UPDATE SKIP LOCKED` 로 한 batch 락을 잡고 PENDING / 만료된 FAILED 행을 가져온다.
     *
     * 트랜잭션 (이슈 #25): 본 메서드는 짧은 자체 트랜잭션 내에서 실행된다.
     * 호출자가 외부 IO 를 수행하는 동안 락이 점유되지 않도록, 행 fetch 후 즉시 트랜잭션을 커밋한다.
     * 락은 해제되지만 다음 폴링 사이클까지 다른 인스턴스가 같은 행을 잡을 수 있다 — 이는 의도된 동작으로,
     * 발행 멱등성 (eventId 를 ES doc id / Kafka key 로 사용) 으로 중복을 흡수한다.
     */
    fun fetchPending(limit: Int): List<Outbox>

    /**
     * 배치 dispatch 성공 행을 한 번의 UPDATE 로 PUBLISHED 처리한다.
     */
    fun markPublishedAll(ids: List<Long>)

    /**
     * 배치 단위 markFailed — 실패 행마다 next_attempt_at / error 가 다르므로 row-by-row UPDATE.
     * 다만 호출자가 외부 IO 밖에서 모아 한 트랜잭션으로 밀어 넣을 수 있도록 batch 형태로 노출.
     */
    fun markFailedAll(failures: List<FailureUpdate>)

    fun markDeadAll(deads: List<DeadUpdate>)

    /**
     * @property id outbox 행 id.
     * @property error redact 처리된 error 메시지.
     * @property nextAttemptAt 백오프 적용된 다음 시도 시각.
     */
    data class FailureUpdate(
        val id: Long,
        val error: String,
        val nextAttemptAt: Instant,
    )

    /**
     * @property id outbox 행 id.
     * @property error redact 처리된 사유.
     */
    data class DeadUpdate(
        val id: Long,
        val error: String,
    )
}
