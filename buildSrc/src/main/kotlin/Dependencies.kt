object Dependencies {

    object Spring {
        // Eureka 대시보드는 MVC 기반이므로 WEB 유지, 그 외 서비스는 WEBFLUX 사용
        const val WEB = "org.springframework.boot:spring-boot-starter-web"
        const val WEBFLUX = "org.springframework.boot:spring-boot-starter-webflux"
        const val ACTUATOR = "org.springframework.boot:spring-boot-starter-actuator"
        const val VALIDATION = "org.springframework.boot:spring-boot-starter-validation"
        const val SECURITY = "org.springframework.boot:spring-boot-starter-security"
        const val KAFKA = "org.springframework.boot:spring-boot-starter-kafka"
        const val ELASTICSEARCH = "org.springframework.boot:spring-boot-starter-data-elasticsearch"

        // Flyway 마이그레이션은 JDBC 모드로 실행 (R2DBC 직접 지원 없음)
        const val JDBC = "org.springframework.boot:spring-boot-starter-jdbc"
    }

    object SpringCloud {
        const val BOM = "org.springframework.cloud:spring-cloud-dependencies:${Versions.SPRING_CLOUD}"
        const val GATEWAY = "org.springframework.cloud:spring-cloud-starter-gateway-server-webflux"
        const val EUREKA_CLIENT = "org.springframework.cloud:spring-cloud-starter-netflix-eureka-client"
        const val RESILIENCE4J_REACTOR = "org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j"
        const val CONFIG_SERVER = "org.springframework.cloud:spring-cloud-config-server"
        const val CONFIG_CLIENT = "org.springframework.cloud:spring-cloud-starter-config"
    }

    object R2dbc {
        const val POSTGRESQL = "org.postgresql:r2dbc-postgresql"
        const val POOL = "io.r2dbc:r2dbc-pool"
    }

    object Kotlin {
        const val REFLECT = "org.jetbrains.kotlin:kotlin-reflect"
    }

    object Ktor {
        const val CLIENT_CORE = "io.ktor:ktor-client-core:${Versions.KTOR}"
        const val CLIENT_CIO = "io.ktor:ktor-client-cio:${Versions.KTOR}"
        const val CLIENT_CONTENT_NEGOTIATION = "io.ktor:ktor-client-content-negotiation:${Versions.KTOR}"
        const val SERIALIZATION_JACKSON = "io.ktor:ktor-serialization-jackson:${Versions.KTOR}"
        const val CLIENT_LOGGING = "io.ktor:ktor-client-logging:${Versions.KTOR}"
    }

    object Exposed {
        const val CORE = "org.jetbrains.exposed:exposed-core:${Versions.EXPOSED}"
        const val R2DBC = "org.jetbrains.exposed:exposed-r2dbc:${Versions.EXPOSED}"
        const val JAVA_TIME = "org.jetbrains.exposed:exposed-java-time:${Versions.EXPOSED}"
        const val JSON = "org.jetbrains.exposed:exposed-json:${Versions.EXPOSED}"
    }

    object Database {
        // Flyway 마이그레이션은 JDBC 드라이버 필요
        const val POSTGRESQL = "org.postgresql:postgresql"
        const val FLYWAY_CORE = "org.flywaydb:flyway-core"
        const val FLYWAY_POSTGRESQL = "org.flywaydb:flyway-database-postgresql"
    }

    object Cache {
        const val CAFFEINE = "com.github.ben-manes.caffeine:caffeine:${Versions.CAFFEINE}"
    }

    object Redisson {
        const val SPRING_BOOT_STARTER = "org.redisson:redisson-spring-boot-starter:${Versions.REDISSON}"
    }

    object Observability {
        const val MICROMETER_PROMETHEUS = "io.micrometer:micrometer-registry-prometheus"
    }

    object Docs {
        const val SPRINGDOC = "org.springdoc:springdoc-openapi-starter-webflux-ui:${Versions.SPRINGDOC}"
    }

    object Jackson {
        const val KOTLIN_MODULE = "tools.jackson.module:jackson-module-kotlin"
    }

    object Frontend {
        const val KTOR_CLIENT_JS = "io.ktor:ktor-client-js:${Versions.KTOR}"
        const val KTOR_SERIALIZATION_KOTLINX_JSON = "io.ktor:ktor-serialization-kotlinx-json:${Versions.KTOR}"
        const val KOTLINX_SERIALIZATION_JSON = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.KOTLINX_SERIALIZATION}"
        const val KOTLINX_COROUTINES = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.KOTLINX_COROUTINES}"
    }

    object Test {
        const val SPRING_BOOT_TEST = "org.springframework.boot:spring-boot-starter-test"
        const val SPRING_SECURITY_TEST = "org.springframework.boot:spring-boot-starter-security-test"
        const val KOTLIN_TEST = "org.jetbrains.kotlin:kotlin-test-junit5"
        const val JUNIT_LAUNCHER = "org.junit.platform:junit-platform-launcher"

        // MockK
        const val MOCKK = "io.mockk:mockk:${Versions.MOCKK}"

        // Kotest
        const val KOTEST_RUNNER = "io.kotest:kotest-runner-junit5:${Versions.KOTEST}"
        const val KOTEST_ASSERTIONS = "io.kotest:kotest-assertions-core:${Versions.KOTEST}"
        const val KOTEST_PROPERTY = "io.kotest:kotest-property:${Versions.KOTEST}"
        const val KOTEST_EXTENSIONS_SPRING = "io.kotest.extensions:kotest-extensions-spring:1.3.0"

        // Coroutines Test
        const val COROUTINES_TEST = "org.jetbrains.kotlinx:kotlinx-coroutines-test"

        // Ktor Client Mock
        const val KTOR_CLIENT_MOCK = "io.ktor:ktor-client-mock:${Versions.KTOR}"

        // Testcontainers
        const val TESTCONTAINERS_BOM = "org.testcontainers:testcontainers-bom:${Versions.TESTCONTAINERS}"
        const val TESTCONTAINERS_JUNIT = "org.testcontainers:junit-jupiter"
        const val TESTCONTAINERS_POSTGRESQL = "org.testcontainers:postgresql"
        const val TESTCONTAINERS_KAFKA = "org.testcontainers:kafka"

        // Spring Kafka Test
        const val SPRING_KAFKA_TEST = "org.springframework.kafka:spring-kafka-test"
    }
}
