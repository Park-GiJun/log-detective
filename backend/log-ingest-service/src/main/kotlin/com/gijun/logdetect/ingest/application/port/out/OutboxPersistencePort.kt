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
     * 배치 단위 markFailed — 어댑터에서 (error, nextAttemptAt) 동일 그룹별로 IN-list UPDATE 한 번씩 묶어 처리한다 (이슈 #89).
     * 같은 dispatch 사이클에서 발생한 동일 외부 IO 실패는 거의 같은 그룹으로 묶여 RTT 1~3 회로 떨어진다.
     */
    fun markFailedAll(failures: List<FailureUpdate>)

    fun markDeadAll(deads: List<DeadUpdate>)

    /**
     * PUBLISHED 상태이면서 published_at 이 [threshold] 이전인 행을 영구 삭제한다.
     * GDPR / 개인정보보호법 보관 기한 준수를 위한 retention 잡 전용 — 영향 행 수를 반환한다.
     */
    fun purgePublishedOlderThan(threshold: Instant): Int

    /**
     * DEAD 상태이면서 created_at 이 [threshold] 이전인 행을 영구 삭제한다.
     * DEAD 행은 운영자가 분석을 마쳤다고 가정하고, 보관 기한 초과 시 일괄 purge.
     * 본 PR 은 단순 purge — archive(S3) 는 별도 이슈로 분리한다. 영향 행 수를 반환한다.
     */
    fun purgeDeadOlderThan(threshold: Instant): Int

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
