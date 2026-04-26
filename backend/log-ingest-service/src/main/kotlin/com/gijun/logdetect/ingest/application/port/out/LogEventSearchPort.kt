package com.gijun.logdetect.ingest.application.port.out

/**
 * Outbox dispatch 가 ES 로 발행할 때 사용하는 포트.
 *
 * WHY — payload 를 LogEvent 로 역직렬화한 뒤 다시 직렬화하는 것은
 * 불필요한 CPU 비용 + 도메인 결합이다. Outbox 에 이미 JSON 으로 저장된 payload 를
 * raw bytes 그대로 ES bulk body 에 흘려보내, dispatch 단계의 직렬화 1회를 제거한다.
 */
interface LogEventSearchPort {

    /**
     * ES `_bulk` API 로 일괄 인덱싱한다.
     *
     * @param documents (index, id, jsonPayload) 트리플 리스트.
     *   index 는 호출자가 결정한 ES 인덱스명, id 는 문서 식별자, jsonPayload 는 raw JSON bytes.
     * @return BulkResult — 부분 실패 시에도 어떤 id 가 실패했는지 호출자가 알 수 있도록 한다.
     */
    fun indexBulk(documents: List<SearchDocument>): BulkResult

    data class SearchDocument(
        val index: String,
        val id: String,
        val payload: ByteArray,
    ) {
        // ByteArray 는 equals/hashCode 가 reference 기반이므로 의도된 비교 의미를 명시.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SearchDocument) return false
            return index == other.index && id == other.id && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = index.hashCode()
            result = 31 * result + id.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    /**
     * @property successIds 발행에 성공한 문서 id (= Outbox 행 id 가 아니라 LogEvent eventId).
     * @property failures (id → 에러 메시지) 매핑.
     */
    data class BulkResult(
        val successIds: Set<String>,
        val failures: Map<String, String>,
    )
}
