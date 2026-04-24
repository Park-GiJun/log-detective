package com.gijun.logdetect.generator.infrastructure.config

import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort
import com.gijun.logdetect.generator.infrastructure.adapter.out.persistence.ScenarioPersistenceAdapter
import io.r2dbc.spi.ConnectionFactory
import org.jetbrains.exposed.v1.r2dbc.R2dbcDatabase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class R2dbcConfig {

    @Bean
    fun exposedDatabase(connectionFactory: ConnectionFactory): R2dbcDatabase =
        R2dbcDatabase.connect(connectionFactory)

    @Bean
    fun scenarioPersistencePort(database: R2dbcDatabase): ScenarioPersistencePort =
        ScenarioPersistenceAdapter(database)
}
