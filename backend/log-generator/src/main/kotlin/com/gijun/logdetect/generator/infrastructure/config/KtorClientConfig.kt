package com.gijun.logdetect.generator.infrastructure.config

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.endpoint
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.jackson.jackson
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KtorClientConfig {

    @Bean(destroyMethod = "close")
    fun httpClient(): HttpClient = HttpClient(CIO) {
        engine {
            maxConnectionsCount = 1000
            endpoint {
                maxConnectionsPerRoute = 100
                connectTimeout = 5000
                requestTimeout = 10000
            }
        }
        install(ContentNegotiation) {
            jackson()
        }
        install(Logging) {
            level = LogLevel.ALL
        }
    }
}
