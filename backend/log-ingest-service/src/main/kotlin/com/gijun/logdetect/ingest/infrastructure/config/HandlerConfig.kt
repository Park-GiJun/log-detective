package com.gijun.logdetect.ingest.infrastructure.config

import com.gijun.logdetect.ingest.application.handler.command.DispatchOutboxHandler
import com.gijun.logdetect.ingest.application.handler.command.LogEventCommandHandler
import com.gijun.logdetect.ingest.application.handler.query.LogEventQueryHandler
import com.gijun.logdetect.ingest.application.port.out.LogEventMessagePort
import com.gijun.logdetect.ingest.application.port.out.LogEventPersistencePort
import com.gijun.logdetect.ingest.application.port.out.LogEventSearchPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPayloadSerializerPort
import com.gijun.logdetect.ingest.application.port.out.OutboxPersistencePort
import com.gijun.logdetect.ingest.application.port.out.SearchIndexResolverPort
import com.gijun.logdetect.ingest.domain.Clock
import com.gijun.logdetect.ingest.infrastructure.util.ErrorRedactor
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
        @Qualifier("outboxDispatchExecutor") outboxDispatchExecutor: Executor,
    ) = DispatchOutboxHandler(
        outboxPersistencePort = outboxPersistencePort,
        logEventSearchPort = logEventSearchPort,
        logEventMessagePort = logEventMessagePort,
        clock = clock,
        // ErrorRedactor 는 inbound 의존이 아니므로 람다로 직접 주입 — 인프라 객체를 application 으로 흘려 보내지 않음.
        errorRedactor = { ErrorRedactor.redact(it) },
        // 채널 병렬 dispatch (이슈 #93) — 전용 풀 주입.
        dispatchExecutor = outboxDispatchExecutor,
    )
}
