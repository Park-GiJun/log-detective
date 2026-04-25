package com.gijun.logdetect.ingest.infrastructure.config

import com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.LogEventPersistenceAdapter
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class R2dbcConfig {

    // Spring Boot 가 `spring.r2dbc.*` 에서 자동 구성한 ConnectionFactory 를 그대로 사용.
    // ConnectionFactory 만 넘기는 단일 인자 시그니처는 없으므로 explicitDialect 를 명시.
    @Bean
    fun exposedDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase =
        R2dbcDatabase.connect(
            connectionFactory = connectionFactory,
            databaseConfig = R2dbcDatabaseConfig.Builder().apply {
                explicitDialect = PostgreSQLDialect()
            },
        )

    @Bean
    fun logEventPersistencePort(database: R2dbcDatabase): LogEventPersistencePort =
        LogEventPersistenceAdapter(database)
}
