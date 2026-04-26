package com.gijun.logdetect.ingest.application.port.out

/**
 * Outbox dispatch 가 Kafka 로 발행할 때 사용하는 포트.
 *
 * WHY — Outbox payload 는 이미 JSON 직렬화된 bytes 이므로,
 * domain LogEvent 로 역직렬화한 뒤 LogEventMessage 로 매핑해 다시 직렬화하는
 * 라운드 트립을 제거한다. KafkaTemplate 의 ByteArray serializer 를 이용해
 * raw bytes 를 그대로 broker 에 보낸다.
 */
interface LogEventMessagePort {

    /**
     * Kafka 로 일괄 발행한다.
     *
     * @param messages (topic, key, jsonPayload) 트리플 리스트.
     * @return BulkResult — broker ack 까지 동기 대기하며 부분 실패 시 어떤 key 가 실패했는지 반환.
     */
    fun publishBulk(messages: List<KafkaMessage>): BulkResult

    data class KafkaMessage(
        val topic: String,
        val key: String,
        val payload: ByteArray,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is KafkaMessage) return false
            return topic == other.topic && key == other.key && payload.contentEquals(other.payload)
        }

        override fun hashCode(): Int {
            var result = topic.hashCode()
            result = 31 * result + key.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

    data class BulkResult(
        val successKeys: Set<String>,
        val failures: Map<String, String>,
    )
}
