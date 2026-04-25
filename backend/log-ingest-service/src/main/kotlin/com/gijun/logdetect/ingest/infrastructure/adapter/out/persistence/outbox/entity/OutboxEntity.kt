package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.entity

import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.enums.OutboxStatus
import com.gijun.logdetect.ingest.domain.model.Outbox
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "outbox_messages", schema = "ingest")
class OutboxEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "aggregate_id", nullable = false, length = 64)
    val aggregateId: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val channel: ChannelType,

    @Column(nullable = false, length = 255)
    val destination: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "JSONB")
    val payload: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val status: OutboxStatus,

    @Column(nullable = false)
    val attempts: Int,

    @Column(name = "next_attempt_at", nullable = false)
    val nextAttemptAt: Instant,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant,

    @Column(name = "published_at")
    val publishedAt: Instant? = null,

    @Column(name = "last_error", columnDefinition = "TEXT")
    val lastError: String? = null,
) {
    fun toDomain(): Outbox = Outbox(
        id = id,
        aggregateId = aggregateId,
        channel = channel,
        destination = destination,
        payload = payload,
        status = status,
        attempts = attempts,
        nextAttemptAt = nextAttemptAt,
        createdAt = createdAt,
        publishedAt = publishedAt,
        lastError = lastError,
    )

    companion object {
        fun from(outbox: Outbox): OutboxEntity = OutboxEntity(
            id = outbox.id,
            aggregateId = outbox.aggregateId,
            channel = outbox.channel,
            destination = outbox.destination,
            payload = outbox.payload,
            status = outbox.status,
            attempts = outbox.attempts,
            nextAttemptAt = outbox.nextAttemptAt,
            createdAt = outbox.createdAt ?: Instant.now(),
            publishedAt = outbox.publishedAt,
            lastError = outbox.lastError,
        )
    }
}
