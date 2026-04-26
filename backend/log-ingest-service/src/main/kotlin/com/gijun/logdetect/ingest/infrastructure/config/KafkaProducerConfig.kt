package com.gijun.logdetect.ingest.infrastructure.config

import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory

/**
 * Outbox dispatch 전용 ByteArray KafkaTemplate.
 *
 * WHY — Outbox payload 는 이미 직렬화된 JSON bytes. 기본 ObjectMapper 기반 JsonSerializer 를
 * 거치면 String → POJO → JsonNode → bytes 라운드 트립이 발생한다.
 * ByteArraySerializer 를 사용해 raw bytes 를 그대로 broker 로 보낸다 (이슈 #54).
 *
 * 또한 batch.size / linger.ms 를 조정해 같은 폴링 사이클에서 모인 메시지가 한 produce
 * request 로 묶이도록 한다 (이슈 #40 — 처리량 향상).
 *
 * 신뢰성/처리량 강화 (이슈 #91):
 * - enable.idempotence=true — acks=all + retries=3 환경에서 중복/순서 깨짐을 producer 측에서 차단.
 * - max.in.flight.requests.per.connection=5 — idempotent producer 에서 허용되는 안전한 최대값.
 *   (Kafka 3.x 기준 idempotence 와 함께 5 까지 순서가 보장된다.)
 * - compression.type=lz4 — JSON payload 특성상 압축률·CPU 비용 균형이 좋고 처리량 ~30% 향상.
 */
@Configuration
class KafkaProducerConfig {

    @Bean
    fun outboxByteArrayProducerFactory(
        kafkaProperties: KafkaProperties,
        @Value("\${logdetect.outbox.kafka.linger-ms:20}") lingerMs: Int,
        @Value("\${logdetect.outbox.kafka.batch-size:65536}") batchSize: Int,
    ): ProducerFactory<String, ByteArray> {
        // Boot 4 의 KafkaProperties.buildProducerProperties 시그니처 호환을 위해 reflection 회피 — 직접 맵 구성.
        val configs: MutableMap<String, Any> = HashMap()
        // bootstrapServers 는 Boot 4 에서 List<String> 으로 노출. ProducerConfig 는 List 또는 csv String 모두 수용한다.
        kafkaProperties.bootstrapServers?.let { configs[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = it }
        configs[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        configs[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = ByteArraySerializer::class.java
        configs[ProducerConfig.ACKS_CONFIG] = "all"
        configs[ProducerConfig.RETRIES_CONFIG] = 3
        configs[ProducerConfig.LINGER_MS_CONFIG] = lingerMs
        configs[ProducerConfig.BATCH_SIZE_CONFIG] = batchSize
        configs[ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG] = 30_000
        configs[ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 10_000
        configs[ProducerConfig.MAX_BLOCK_MS_CONFIG] = 30_000
        // 이슈 #91 — 신뢰성/처리량 강화
        configs[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        configs[ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION] = 5
        configs[ProducerConfig.COMPRESSION_TYPE_CONFIG] = "lz4"
        return DefaultKafkaProducerFactory(configs)
    }

    @Bean
    fun outboxKafkaTemplate(
        outboxByteArrayProducerFactory: ProducerFactory<String, ByteArray>,
    ): KafkaTemplate<String, ByteArray> = KafkaTemplate(outboxByteArrayProducerFactory)
}
