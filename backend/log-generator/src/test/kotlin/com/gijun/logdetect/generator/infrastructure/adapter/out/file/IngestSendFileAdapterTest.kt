package com.gijun.logdetect.generator.infrastructure.adapter.out.file

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.gijun.logdetect.generator.domain.model.LogEvent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.time.Instant

class IngestSendFileAdapterTest : DescribeSpec({

    val mapper = ObjectMapper().registerModule(JavaTimeModule())

    fun event(idx: Long) = LogEvent(
        id = idx,
        transactionId = "tx-$idx",
        source = "src",
        level = "INFO",
        message = "m-$idx",
        timestamp = Instant.parse("2026-04-26T10:00:00Z"),
        host = "host",
        ip = "10.0.0.1",
        userId = idx,
        attributes = mapOf("k" to "v"),
    )

    describe("동시 쓰기 안전성") {
        it("100 개 코루틴이 동시에 append 해도 라인 손실 / 인터리빙 없음") {
            val tempFile = Files.createTempFile("ingest-send-test", ".log")
            tempFile.toFile().deleteOnExit()
            // Files.createDirectories(parent) 가 호출되도록 빈 파일 삭제
            Files.deleteIfExists(tempFile)

            val adapter = IngestSendFileAdapter(mapper, tempFile.toString())
            val total = 100

            runBlocking {
                coroutineScope {
                    (1..total).map { i ->
                        async(Dispatchers.IO) {
                            adapter.send(event(i.toLong()))
                        }
                    }.awaitAll()
                }
            }

            val lines = withContext(Dispatchers.IO) {
                Files.readAllLines(tempFile)
            }
            lines shouldHaveSize total
            // 각 라인이 온전한 JSON 인지 (인터리빙 없음) — 파싱 통과로 검증
            lines.forEach { line ->
                val parsed = mapper.readTree(line)
                parsed.has("transactionId") shouldBe true
            }
        }
    }
})
