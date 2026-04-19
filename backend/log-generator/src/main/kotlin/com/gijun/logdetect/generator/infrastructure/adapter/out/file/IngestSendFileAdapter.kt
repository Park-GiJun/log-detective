package com.gijun.logdetect.generator.infrastructure.adapter.out.file

import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.generator.application.port.out.IngestSendFilePort
import com.gijun.logdetect.generator.domain.model.LogEvent
import com.gijun.logdetect.generator.infrastructure.adapter.out.client.dto.IngestSendRequest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Component
class IngestSendFileAdapter(
    private val objectMapper: ObjectMapper,
    @Value("\${generator.file.output-path}") private val outputPath: String,
) : IngestSendFilePort {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    override suspend fun send(log: LogEvent): Boolean {
        return try {
            val json = objectMapper.writeValueAsString(IngestSendRequest.from(log))
            val path = Path.of(outputPath)
            Files.createDirectories(path.parent)
            Files.writeString(
                path,
                json + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
            true
        } catch (e: Exception) {
            logger.error("파일 기록 실패 — transactionId: {}", log.transactionId, e)
            false
        }
    }
}
