package com.gijun.logdetect.ingest.integration

import co.elastic.clients.elasticsearch.ElasticsearchClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.domain.enums.OutboxStatus
import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.outbox.repository.OutboxJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.client.RestTemplate
import java.time.Duration
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class LogIngestE2EIntegrationTest : IntegrationTestBase() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var outboxRepository: OutboxJpaRepository

    @Autowired
    private lateinit var outboxPersistencePort: OutboxPersistencePort

    @Autowired
    private lateinit var elasticsearchClient: ElasticsearchClient

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var clock: com.gijun.logdetect.ingest.domain.port.Clock

    private val rest = RestTemplate()

    @BeforeEach
    fun cleanupOutbox() {
        outboxRepository.deleteAll()
        // ES 의 자동 인덱스 생성이 첫 호출에서 timeout 으로 실패하지 않도록 미리 생성.
        // (운영에서는 첫 트래픽 들어오기 전 미리 cluster 가 안정화되어 OK 이므로 테스트 환경 보정.)
        // 시각 하드코딩 제거 — clock 빈으로부터 인덱스 날짜를 도출해 테스트 실행일과 어긋나지 않게 한다 (이슈 #99).
        val today = "logs-${clock.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}"
        runCatching { elasticsearchClient.indices().create { it.index(today) } }
    }

    @Test
    fun `POST api v1 logs - log_events 1행 + outbox 2행이 생성되고 OutboxPublisher 가 ES + Kafka 로 발행하여 PUBLISHED 전이`() {
        val timestamp = clock.now()
        val body = """
            {
              "source": "integration-test",
              "level": "INFO",
              "message": "hello-from-test",
              "timestamp": "$timestamp",
              "host": "host-test",
              "ip": "10.0.0.99",
              "userId": "user-test",
              "attributes": {"k": "v"}
            }
        """.trimIndent()

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = rest.postForEntity(
            "http://localhost:$port/api/v1/logs",
            HttpEntity(body, headers),
            String::class.java,
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)

        // outbox 행 2개 (ES + KAFKA) 가 만들어진다.
        // 폴링 주기가 짧아 PENDING 검증 직후 PUBLISHED 로 전이될 수 있어, 채널 구성과 최종 상태 (PUBLISHED) 만 단언.
        val rows = outboxRepository.findAll()
        assertThat(rows).hasSize(2)
        assertThat(rows.map { it.channel.name }).containsExactlyInAnyOrder("ES", "KAFKA")

        await()
            .atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted {
                val all = outboxRepository.findAll()
                val notPublished = all.filter { it.status != OutboxStatus.PUBLISHED }
                if (notPublished.isNotEmpty()) {
                    println(
                        "OUTBOX-DEBUG: " +
                            notPublished.joinToString("; ") {
                                "ch=${it.channel} status=${it.status} attempts=${it.attempts} err=${it.lastError}"
                            },
                    )
                }
                assertThat(all).allMatch { it.status == OutboxStatus.PUBLISHED }
            }

        val esIndex = "logs-${timestamp.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}"

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted {
                elasticsearchClient.indices().refresh { it.index(esIndex) }
                val count = elasticsearchClient.count { it.index(esIndex) }.count()
                assertThat(count).isEqualTo(1L)
            }
    }

    @Test
    fun `미지원 채널 (FILE) outbox 행은 OutboxPublisher 가 즉시 markDead 처리`() {
        val event = com.gijun.logdetect.common.domain.model.LogEvent(
            eventId = java.util.UUID.randomUUID(),
            source = "integration",
            level = com.gijun.logdetect.common.domain.enums.LogLevel.INFO,
            message = "m",
            timestamp = clock.now(),
        )
        val payload = objectMapper.writeValueAsString(event)
        outboxPersistencePort.saveAll(
            listOf(
                com.gijun.logdetect.ingest.domain.model.Outbox.newPending(
                    clock = clock,
                    aggregateId = event.eventId.toString(),
                    channel = com.gijun.logdetect.ingest.domain.enums.ChannelType.FILE,
                    destination = "/tmp/file",
                    payload = payload,
                ),
            ),
        )

        await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(1))
            .untilAsserted {
                val all = outboxRepository.findAll()
                assertThat(all).hasSize(1)
                assertThat(all.first().status).isEqualTo(OutboxStatus.DEAD)
                assertThat(all.first().lastError).contains("unsupported channel")
            }
    }
}
