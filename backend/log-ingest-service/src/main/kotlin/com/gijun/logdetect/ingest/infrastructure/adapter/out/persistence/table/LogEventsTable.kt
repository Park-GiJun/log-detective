package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.table

import com.gijun.logdetect.common.domain.enums.LogLevel
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.json.jsonb
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// Flyway 로 생성된 ingest.log_events 테이블 매핑.
object LogEventsTable : Table("ingest.log_events") {
    val id = long("id").autoIncrement()
    val eventId = javaUUID("event_id").uniqueIndex()

    // ColumnSet.source 와 충돌 방지를 위해 변수명만 sourceCol 로 선언 (DB 컬럼명은 "source" 유지).
    val sourceCol = varchar("source", 128)
    val level = enumerationByName("level", 16, LogLevel::class)
    val message = text("message")
    val eventTimestamp = timestampWithTimeZone("event_timestamp")
    val host = varchar("host", 255).nullable()

    // INET 컬럼은 R2DBC 단에서 문자열로 처리. Exposed 는 varchar 로 매핑.
    val ip = varchar("ip", 64).nullable()
    val userId = varchar("user_id", 128).nullable()
    val attributes = jsonb(
        "attributes",
        serialize = { value: Map<String, String> -> AttributesCodec.encode(value) },
        deserialize = { raw -> AttributesCodec.decode(raw) },
    ).nullable()
    val ingestedAt = timestampWithTimeZone("ingested_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
