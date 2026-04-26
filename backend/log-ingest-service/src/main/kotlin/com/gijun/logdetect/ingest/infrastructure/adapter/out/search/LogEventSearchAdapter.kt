package com.gijun.logdetect.ingest.infrastructure.adapter.out.search

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort.BulkResult
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort.SearchDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream

/**
 * ES bulk 발행 어댑터.
 *
 * WHY — Outbox payload 가 이미 직렬화된 JSON bytes 이므로, ES Java client 의
 * `BulkRequest.withJson(InputStream)` 에 NDJSON body 를 직접 흘려보낸다.
 * Outbox payload 를 LogEvent 로 deserialize 한 뒤 다시 serialize 하는 라운드 트립을 제거한다 (이슈 #54).
 *
 * 인덱스명 결정 (`logs-yyyy.MM.dd`) 은 [SearchIndexResolver] 가 책임진다.
 *
 * NDJSON format (한 메타 라인 + 한 source 라인 반복):
 * ```
 * {"index":{"_index":"logs-2026.04.26","_id":"<eventId>"}}
 * {<원본 LogEvent JSON>}
 * ```
 */
@Component
class LogEventSearchAdapter(
    private val elasticsearchClient: ElasticsearchClient,
) : LogEventSearchPort {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    override fun indexBulk(documents: List<SearchDocument>): BulkResult {
        if (documents.isEmpty()) {
            return BulkResult(successIds = emptySet(), failures = emptyMap())
        }

        // NDJSON body 작성 — 메타 + payload 를 줄바꿈으로 이어 붙인다.
        val body = buildBulkBody(documents)

        val response = elasticsearchClient.bulk { bulk ->
            bulk.withJson(ByteArrayInputStream(body))
        }

        // 부분 실패 처리 — items 순서가 요청 순서와 동일.
        val failures = mutableMapOf<String, String>()
        if (response.errors()) {
            response.items().forEachIndexed { i, item ->
                val err = item.error()
                if (err != null) {
                    val id = documents[i].id
                    failures[id] = err.reason() ?: err.type() ?: "unknown ES error"
                }
            }
            logger.warn("ES bulk 부분 실패 — 총 {} 건 중 {} 건 실패", documents.size, failures.size)
        } else {
            logger.debug("ES bulk 인덱싱 완료 — {} 건", documents.size)
        }

        val successIds = documents.asSequence()
            .map { it.id }
            .filter { it !in failures }
            .toSet()
        return BulkResult(successIds = successIds, failures = failures)
    }

    private fun buildBulkBody(documents: List<SearchDocument>): ByteArray {
        // pre-size 추정 — 메타 + payload + 줄바꿈. 약간 넉넉하게 잡아 grow 비용 감소.
        val estimated = documents.sumOf { 128 + it.payload.size }
        val out = java.io.ByteArrayOutputStream(estimated)
        documents.forEach { doc ->
            // 메타 라인 — string 보간 후 toByteArray 라운드 트립을 피하기 위해 고정 segment 들을
            // 직접 ByteArrayOutputStream 으로 흘려 쓴다 (이슈 #99). _index / _id 는 ASCII 안전 가정
            // (UUID + logs-yyyy.MM.dd) 이라 JSON escape 불필요.
            out.write(META_PREFIX)
            out.write(doc.index.toByteArray(Charsets.UTF_8))
            out.write(META_INFIX)
            out.write(doc.id.toByteArray(Charsets.UTF_8))
            out.write(META_SUFFIX)
            out.write(NEWLINE)
            out.write(doc.payload)
            out.write(NEWLINE)
        }
        return out.toByteArray()
    }

    companion object {
        private val NEWLINE = "\n".toByteArray(Charsets.UTF_8)
        // {"index":{"_index":"
        private val META_PREFIX = """{"index":{"_index":"""".toByteArray(Charsets.UTF_8)
        // ","_id":"
        private val META_INFIX = """","_id":"""".toByteArray(Charsets.UTF_8)
        // "}}
        private val META_SUFFIX = """"}}""".toByteArray(Charsets.UTF_8)
    }
}
