package com.gijun.logdetect.ingest

import com.gijun.logdetect.ingest.infrastructure.config.IngestSecurityProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(IngestSecurityProperties::class)
class IngestServiceApplication

fun main(args: Array<String>) {
    runApplication<IngestServiceApplication>(*args)
}
