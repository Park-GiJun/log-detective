package com.gijun.logdetect.generator.infrastructure.adapter.out.file

import com.fasterxml.jackson.databind.ObjectMapper
import com.gijun.logdetect.generator.application.port.out.IngestSendFilePort
import com.gijun.logdetect.generator.domain.model.LogEvent
import com.gijun.logdetect.generator.infrastructure.adapter.out.client.dto.IngestSendRequest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

@Component
class IngestSendFileAdapter(
    private val objectMapper: ObjectMapper,
    @Value("\${generator.file.output-path}") private val outputPath: String,
) : IngestSendFilePort {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    // 동일 파일 경로에 다중 코루틴이 동시에 append 하면 라인 인터리빙 / 부분 쓰기 발생.
    // 경로별 Mutex 로 직렬화 — 다른 경로는 병렬 유지하여 처리량 손실 최소화.
    private val pathLocks = ConcurrentHashMap<Path, Mutex>()

    override suspend fun send(log: LogEvent): Boolean {
        return try {
            val json = objectMapper.writeValueAsString(IngestSendRequest.from(log))
            val path = Path.of(outputPath).toAbsolutePath().normalize()
            val mutex = pathLocks.computeIfAbsent(path) { Mutex() }
            mutex.withLock {
                Files.createDirectories(path.parent)
                Files.writeString(
                    path,
                    json + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND,
                )
            }
            true
        } catch (e: Exception) {
            logger.error("파일 기록 실패 — transactionId: {}", log.transactionId, e)
            false
        }
    }
}
