package com.gijun.logdetect.generator.infrastructure.config

import com.gijun.logdetect.generator.application.handler.command.GeneratorCommandHandler
import com.gijun.logdetect.generator.application.handler.command.ScenarioCommandHandler
import com.gijun.logdetect.generator.application.handler.query.GeneratorQueryHandler
import com.gijun.logdetect.generator.application.handler.query.ScenarioQueryHandler
import com.gijun.logdetect.generator.application.port.out.GeneratorStateCachePort
import com.gijun.logdetect.generator.application.port.out.IngestSendClientPort
import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class HandlerConfig {

    @Bean
    fun generatorCommandHandler(
        ingestSendClientPort: IngestSendClientPort,
        generatorStateCachePort: GeneratorStateCachePort,
    ) = GeneratorCommandHandler(ingestSendClientPort, generatorStateCachePort)

    @Bean
    fun generatorQueryHandler(
        generatorStateCachePort: GeneratorStateCachePort,
    ) = GeneratorQueryHandler(generatorStateCachePort)

    @Bean
    fun scenarioCommandHandler(
        scenarioPersistencePort: ScenarioPersistencePort,
    ) = ScenarioCommandHandler(scenarioPersistencePort)

    @Bean
    fun scenarioQueryHandler(
        scenarioPersistencePort: ScenarioPersistencePort,
    ) = ScenarioQueryHandler(scenarioPersistencePort)
}
