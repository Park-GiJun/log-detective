package com.gijun.logdetect.ingest.integration

import com.gijun.logdetect.common.topic.KafkaTopics
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.time.Duration

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
abstract class IntegrationTestBase {

    companion object {
        private val postgres = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("logdetect")
            .withUsername("test")
            .withPassword("test")

        private val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))

        // ES 9 startup 로그 패턴이 testcontainers ElasticsearchContainer 의 default wait
        // strategy 와 호환되지 않아 GenericContainer + HTTP wait 로 직접 구성한다.
        private val elasticsearch: GenericContainer<*> =
            GenericContainer(DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.0.1"))
                .withExposedPorts(9200)
                .withEnv("discovery.type", "single-node")
                .withEnv("xpack.security.enabled", "false")
                .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                .waitingFor(
                    Wait.forHttp("/_cluster/health")
                        .forStatusCode(200)
                        .withStartupTimeout(Duration.ofMinutes(3)),
                )

        init {
            postgres.start()
            kafka.start()
            elasticsearch.start()
            createKafkaTopics()
        }

        private fun createKafkaTopics() {
            val props = mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers)
            AdminClient.create(props).use { admin ->
                admin.createTopics(listOf(NewTopic(KafkaTopics.LOGS_RAW, 1, 1.toShort()))).all().get()
            }
        }

        @JvmStatic
        @DynamicPropertySource
        fun overrideProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }

            registry.add("spring.flyway.url") { postgres.jdbcUrl }
            registry.add("spring.flyway.user") { postgres.username }
            registry.add("spring.flyway.password") { postgres.password }

            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }

            registry.add("spring.elasticsearch.uris") {
                "http://${elasticsearch.host}:${elasticsearch.getMappedPort(9200)}"
            }
        }
    }
}
