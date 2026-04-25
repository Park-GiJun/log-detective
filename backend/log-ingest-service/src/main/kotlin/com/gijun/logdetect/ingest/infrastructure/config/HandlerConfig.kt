package com.gijun.logdetect.ingest.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.ingest.application.handler.command.LogEventCommandHandler
import com.gijun.logdetect.ingest.application.handler.query.LogEventQueryHandler
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HandlerConfig {

    @Bean
    fun logEventCommandHandler(
        logEventPersistencePort: LogEventPersistencePort,
        outboxPersistencePort: OutboxPersistencePort,
        objectMapper: ObjectMapper,
    ) = LogEventCommandHandler(logEventPersistencePort, outboxPersistencePort, objectMapper)

    @Bean
    fun logEventQueryHandler(
        logEventPersistencePort: LogEventPersistencePort,
    ) = LogEventQueryHandler(logEventPersistencePort)
}
