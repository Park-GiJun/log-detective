package com.gijun.logdetect.ingest.infrastructure.config

import com.gijun.logdetect.ingest.application.handler.command.DispatchOutboxHandler
import com.gijun.logdetect.ingest.application.handler.command.LogEventCommandHandler
import com.gijun.logdetect.ingest.application.handler.query.LogEventQueryHandler
import com.gijun.logdetect.ingest.application.port.out.ErrorRedactorPort
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPayloadSerializerPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.application.port.out.SearchIndexResolverPort
import com.gijun.logdetect.ingest.domain.port.Clock
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executor

@Configuration
class HandlerConfig {

    @Bean
    fun logEventCommandHandler(
        logEventPersistencePort: LogEventPersistencePort,
        outboxPersistencePort: OutboxPersistencePort,
        payloadSerializerPort: OutboxPayloadSerializerPort,
        searchIndexResolverPort: SearchIndexResolverPort,
        clock: Clock,
    ) = LogEventCommandHandler(
        logEventPersistencePort,
        outboxPersistencePort,
        payloadSerializerPort,
        searchIndexResolverPort,
        clock,
    )

    @Bean
    fun logEventQueryHandler(
        logEventPersistencePort: LogEventPersistencePort,
    ) = LogEventQueryHandler(logEventPersistencePort)

    @Bean
    fun dispatchOutboxHandler(
        outboxPersistencePort: OutboxPersistencePort,
        logEventSearchPort: LogEventSearchPort,
        logEventMessagePort: LogEventMessagePort,
        clock: Clock,
        errorRedactorPort: ErrorRedactorPort,
        @Qualifier("outboxDispatchExecutor") outboxDispatchExecutor: Executor,
    ) = DispatchOutboxHandler(
        outboxPersistencePort = outboxPersistencePort,
        logEventSearchPort = logEventSearchPort,
        logEventMessagePort = logEventMessagePort,
        clock = clock,
        // ErrorRedactorPort 는 application/port/out 으로 분리되어 (#97) 빈 주입으로 가져온다.
        errorRedactor = errorRedactorPort,
        // 채널 병렬 dispatch (이슈 #93) — 전용 풀 주입.
        dispatchExecutor = outboxDispatchExecutor,
    )
}
