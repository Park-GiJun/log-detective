package com.gijun.logdetect.ingest.application.port.out

import com.gijun.logdetect.ingest.domain.model.Outbox
import java.time.Instant

interface OutboxPersistencePort {
    fun saveAll(outboxes: List<Outbox>)
    fun fetchPending(limit: Int): List<Outbox>
    fun markPublished(id: Long)

    /**
     * 배치 dispatch 성공 행을 한 번의 UPDATE 로 PUBLISHED 처리한다.
     * 100건 forEach 로 행마다 markPublished 를 호출하면 UPDATE 가 N 번 나가므로,
     * 성공 id 를 모아 IN-list 로 일괄 처리한다.
     */
    fun markPublishedAll(ids: List<Long>)

    fun markFailed(id: Long, error: String, nextAttemptAt: Instant)
    fun markDead(id: Long, error: String)
}
