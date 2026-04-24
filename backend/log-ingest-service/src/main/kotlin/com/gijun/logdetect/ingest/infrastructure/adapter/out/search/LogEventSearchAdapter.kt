package com.gijun.logdetect.ingest.infrastructure.adapter.out.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import co.elastic.clients.elasticsearch.core.BulkRequest
import com.gijun.logdetect.common.domain.model.LogEvent
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Elasticsearch 동기 클라이언트 유지 — Dispatchers.IO 로 블로킹 호출 분리.
@Component
class LogEventSearchAdapter(
    private val elasticsearchClient: ElasticsearchClient,
) : LogEventSearchPort {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun index(event: LogEvent) = withContext(Dispatchers.IO) {
        val indexName = resolveIndexName(event)
        elasticsearchClient.index { builder ->
            builder
                .index(indexName)
                .id(event.eventId.toString())
                .document(toDocument(event))
        }
        logger.debug("ES 인덱싱 — index: {}, eventId: {}", indexName, event.eventId)
    }

    override suspend fun indexBatch(events: List<LogEvent>) {
        if (events.isEmpty()) return
        withContext(Dispatchers.IO) {
            val bulkRequest = BulkRequest.Builder()
            events.forEach { event ->
                bulkRequest.operations { op ->
                    op.index { idx ->
                        idx
                            .index(resolveIndexName(event))
                            .id(event.eventId.toString())
                            .document(toDocument(event))
                    }
                }
            }
            val response = elasticsearchClient.bulk(bulkRequest.build())
            if (response.errors()) {
                logger.warn("ES 벌크 인덱싱 중 오류 발생 — {} 건 중 일부 실패", events.size)
            } else {
                logger.debug("ES 벌크 인덱싱 완료 — {} 건", events.size)
            }
        }
    }

    private fun resolveIndexName(event: LogEvent): String {
        val date = event.timestamp.atOffset(ZoneOffset.UTC).format(DATE_FORMAT)
        return "$INDEX_PREFIX$date"
    }

    private fun toDocument(event: LogEvent): Map<String, Any?> = mapOf(
        "eventId" to event.eventId.toString(),
        "source" to event.source,
        "level" to event.level.name,
        "message" to event.message,
        "timestamp" to event.timestamp.toString(),
        "host" to event.host,
        "ip" to event.ip,
        "userId" to event.userId,
        "attributes" to event.attributes,
    )

    companion object {
        private const val INDEX_PREFIX = "logs-"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    }
}
