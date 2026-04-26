package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.repository

import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.entity.OutboxEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

/**
 * NOTE: `fetchPendingForUpdate` 는 `FOR UPDATE SKIP LOCKED` 가 JPQL 로 표현 불가하여 native SQL 을 사용한다.
 * 이로 인해 `from ingest.outbox_messages` 스키마/테이블명이 [OutboxEntity.@Table] 어노테이션과 이중 관리된다.
 * 스키마 변경 시 [OutboxEntity] 의 `@Table(schema = "ingest", name = "outbox_messages")` 와 반드시 동기화할 것.
 */
interface OutboxJpaRepository : JpaRepository<OutboxEntity, Long> {

    /**
     * 정렬 기준: `next_attempt_at, id`
     * - 재시도(FAILED → PENDING) 행이 next_attempt_at 도달 후 신규 INSERT 행에 밀리지 않도록 시간 순 우선.
     * - id 만 기준으로 하면 신규 이벤트가 항상 먼저 잡혀 윈도우 룰의 시계열 정확성이 흔들린다 (R003 등).
     */
    @Query(
        value = """
            select * from ingest.outbox_messages
            where status in ('PENDING', 'FAILED') and next_attempt_at <= now()
            order by next_attempt_at, id
            limit :limit
            for update skip locked
        """,
        nativeQuery = true,
    )
    fun fetchPendingForUpdate(@Param("limit") limit: Int): List<OutboxEntity>

    /**
     * 배치 dispatch 성공 id 들을 한 번의 UPDATE 로 PUBLISHED 처리한다.
     * row-by-row UPDATE 의 N+1 회피 목적.
     */
    @Modifying
    @Query(
        """
            update OutboxEntity o
            set o.status = com.gijun.logdetect.ingest.domain.enums.OutboxStatus.PUBLISHED,
                o.publishedAt = :publishedAt
            where o.id in :ids
        """,
    )
    fun markPublishedAll(@Param("ids") ids: List<Long>, @Param("publishedAt") publishedAt: Instant): Int

    @Modifying
    @Query(
        """
            update OutboxEntity o
            set o.status = com.gijun.logdetect.ingest.domain.enums.OutboxStatus.FAILED,
                o.attempts = o.attempts + 1,
                o.lastError = :error,
                o.nextAttemptAt = :nextAttemptAt
            where o.id = :id
        """,
    )
    fun markFailed(
        @Param("id") id: Long,
        @Param("error") error: String,
        @Param("nextAttemptAt") nextAttemptAt: Instant,
    ): Int

    @Modifying
    @Query(
        """
            update OutboxEntity o
            set o.status = com.gijun.logdetect.ingest.domain.enums.OutboxStatus.DEAD,
                o.attempts = o.attempts + 1,
                o.lastError = :error
            where o.id = :id
        """,
    )
    fun markDead(@Param("id") id: Long, @Param("error") error: String): Int
}
