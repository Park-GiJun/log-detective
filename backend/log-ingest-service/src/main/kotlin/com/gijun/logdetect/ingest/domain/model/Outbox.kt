package com.gijun.logdetect.ingest.domain.model

import com.gijun.logdetect.ingest.domain.enums.ChannelType
import com.gijun.logdetect.ingest.domain.enums.OutboxStatus
import com.gijun.logdetect.ingest.domain.port.Clock
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
    val createdAt: Instant,
    val publishedAt: Instant? = null,
    val lastError: String? = null,
) {
    companion object {
        fun newPending(
            clock: Clock,
            aggregateId: String,
            channel: ChannelType,
            destination: String,
            payload: String,
        ): Outbox {
            val now = clock.now()
            return Outbox(
                id = null,
                aggregateId = aggregateId,
                channel = channel,
                destination = destination,
                payload = payload,
                status = OutboxStatus.PENDING,
                attempts = 0,
                nextAttemptAt = now,
                createdAt = now,
                publishedAt = null,
                lastError = null,
            )
        }
    }
}
