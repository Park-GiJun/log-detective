package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence

import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.table.LogEventsTable
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.table.LogEventsTable.eventTimestamp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.batchInsert
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class LogEventPersistenceAdapter(
    private val database: R2dbcDatabase,
) : LogEventPersistencePort {

    override suspend fun save(event: LogEvent): Pair<LogEvent, Instant> =
        suspendTransaction(db = database) {
            val inserted = LogEventsTable.insert { row ->
                row[eventId] = event.eventId
                row[sourceCol] = event.source
                row[level] = event.level
                row[message] = event.message
                row[eventTimestamp] = event.timestamp.atZone(KST).toOffsetDateTime()
                row[host] = event.host
                row[ip] = event.ip
                row[userId] = event.userId
                row[attributes] = event.attributes.ifEmpty { null }
            }
            event to inserted[LogEventsTable.ingestedAt].toInstant()
        }

    override suspend fun saveAll(events: List<LogEvent>): List<Pair<LogEvent, Instant>> {
        if (events.isEmpty()) return emptyList()
        return suspendTransaction(db = database) {
            val inserted = LogEventsTable.batchInsert(events) { event ->
                this[LogEventsTable.eventId] = event.eventId
                this[LogEventsTable.sourceCol] = event.source
                this[LogEventsTable.level] = event.level
                this[LogEventsTable.message] = event.message
                this[LogEventsTable.eventTimestamp] = event.timestamp.atZone(KST).toOffsetDateTime()
                this[LogEventsTable.host] = event.host
                this[LogEventsTable.ip] = event.ip
                this[LogEventsTable.userId] = event.userId
                this[LogEventsTable.attributes] = event.attributes.ifEmpty { null }
            }
            val ingestedAtByEventId: Map<UUID, Instant> = inserted.associate {
                it[LogEventsTable.eventId] to it[LogEventsTable.ingestedAt].toInstant()
            }
            val now = Instant.now()
            events.map { event -> event to (ingestedAtByEventId[event.eventId] ?: now) }
        }
    }

    override suspend fun findByEventId(eventId: UUID): Pair<LogEvent, Instant>? =
        suspendTransaction(db = database) {
            LogEventsTable.selectAll()
                .where { LogEventsTable.eventId eq eventId }
                .map { it.toDomain() }
                .toList()
                .firstOrNull()
        }

    override suspend fun findRecent(limit: Int): List<Pair<LogEvent, Instant>> =
        suspendTransaction(db = database) {
            LogEventsTable.selectAll()
                .orderBy(LogEventsTable.eventTimestamp to SortOrder.DESC)
                .limit(limit)
                .map { it.toDomain() }
                .toList()
        }

    private companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }

    private fun ResultRow.toDomain(): Pair<LogEvent, Instant> {
        val event = LogEvent(
            eventId = this[LogEventsTable.eventId],
            source = this[LogEventsTable.sourceCol],
            level = this[LogEventsTable.level],
            message = this[LogEventsTable.message],
            timestamp = this[LogEventsTable.eventTimestamp].toInstant(),
            host = this[LogEventsTable.host],
            ip = this[LogEventsTable.ip],
            userId = this[LogEventsTable.userId],
            attributes = this[LogEventsTable.attributes] ?: emptyMap(),
        )
        return event to this[LogEventsTable.ingestedAt].toInstant()
    }
}
