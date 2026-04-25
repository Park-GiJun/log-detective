package com.gijun.logdetect.ingest.domain.model

import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.enums.OutboxStatus
import java.time.Instant

data class Outbox(
    val id: Long? = null,
    val aggregateId: String,
    val channel: ChannelType,
    val destination: String,
    val payload: String,
    val status: OutboxStatus,
    val attempts: Int,
    val nextAttemptAt: Instant,
    val createdAt: Instant? = Instant.now(),
    val publishedAt: Instant? = null,
    val lastError: String? = null,
) {
    companion object {
        fun newPending(
            aggregateId: String,
            channel: ChannelType,
            destination: String,
            payload: String,
        ): Outbox = Outbox(
            id = null,
            aggregateId = aggregateId,
            channel = channel,
            destination = destination,
            payload = payload,
            status = OutboxStatus.PENDING,
            attempts = 0,
            nextAttemptAt = Instant.now(),
            createdAt = Instant.now(),
            publishedAt = null,
            lastError = null,
        )
    }
}
