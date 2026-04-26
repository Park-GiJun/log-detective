package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.repository

import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.entity.OutboxEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface OutboxJpaRepository : JpaRepository<OutboxEntity, Long> {

    @Query(
        value = """
            select * from ingest.outbox_messages
            where status in ('PENDING', 'FAILED') and next_attempt_at <= now()
            order by id
            limit :limit
            for update skip locked
        """,
        nativeQuery = true,
    )
    fun fetchPendingForUpdate(@Param("limit") limit: Int): List<OutboxEntity>

    @Modifying
    @Query(
        """
            update OutboxEntity o
            set o.status = com.gijun.logdetect.ingest.domain.enums.OutboxStatus.PUBLISHED,
                o.publishedAt = :publishedAt
            where o.id = :id
        """,
    )
    fun markPublished(@Param("id") id: Long, @Param("publishedAt") publishedAt: Instant): Int

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
