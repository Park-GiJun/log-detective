package com.gijun.logdetect.generator.infrastructure.config

import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort
import com.gijun.logdetect.generator.infrastructure.adapter.out.persistence.ScenarioPersistenceAdapter
import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.core.vendors.PostgreSQLDialect
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabaseConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class R2dbcConfig {

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
    fun scenarioPersistencePort(database: R2dbcDatabase): ScenarioPersistencePort =
        ScenarioPersistenceAdapter(database)
}
