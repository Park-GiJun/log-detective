package com.gijun.logdetect.ingest.infrastructure.config

import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.LogEventPersistenceAdapter
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class R2dbcConfig {

    // Spring Boot 가 `spring.r2dbc.*` 에서 자동 구성한 ConnectionFactory 를 그대로 사용.
    @Bean
    fun exposedDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase =
        R2dbcDatabase.connect(connectionFactory)

    @Bean
    fun logEventPersistencePort(database: R2dbcDatabase): LogEventPersistencePort =
        LogEventPersistenceAdapter(database)
}
