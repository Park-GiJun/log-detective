package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.logEvent.entity

import com.gijun.logdetect.common.domain.enums.LogLevel
import com.gijun.logdetect.common.domain.model.LogEvent
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "log_events", schema = "ingest")
class LogEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "event_id", nullable = false, unique = true)
    val eventId: UUID,

    @Column(nullable = false, length = 128)
    val source: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    val level: LogLevel,

    @Column(nullable = false, columnDefinition = "TEXT")
    val message: String,

    @Column(name = "event_timestamp", nullable = false)
    val eventTimestamp: Instant,

    @Column(length = 255)
    val host: String? = null,

    @Column(columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    val ip: String? = null,

    @Column(name = "user_id", length = 128)
    val userId: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    val attributes: Map<String, String>? = null,

    @Column(name = "ingested_at", nullable = false, insertable = false, updatable = false)
    val ingestedAt: Instant? = null,
) {
    fun toDomain(): LogEvent = LogEvent(
        eventId = eventId,
        source = source,
        level = level,
        message = message,
        timestamp = eventTimestamp,
        host = host,
        ip = ip,
        userId = userId,
        attributes = attributes ?: emptyMap(),
    )

    companion object {
        fun from(event: LogEvent): LogEventEntity = LogEventEntity(
            eventId = event.eventId,
            source = event.source,
            level = event.level,
            message = event.message,
            eventTimestamp = event.timestamp,
            host = event.host,
            ip = event.ip,
            userId = event.userId,
            attributes = event.attributes.ifEmpty { null },
        )
    }
}
