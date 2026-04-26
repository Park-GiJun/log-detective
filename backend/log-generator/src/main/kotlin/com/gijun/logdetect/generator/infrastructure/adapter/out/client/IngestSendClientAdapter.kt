package com.gijun.logdetect.generator.infrastructure.adapter.out.client

import com.gijun.logdetect.generator.application.port.out.IngestSendClientPort
import com.gijun.logdetect.generator.domain.model.LogEvent
import com.gijun.logdetect.generator.infrastructure.adapter.out.client.dto.IngestSendRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class IngestSendClientAdapter(
    private val httpClient: HttpClient,
    @Value("\${generator.target-url}") private val targetUrl: String,
    @Value("\${generator.ssrf.allow-private-network:false}") private val allowPrivateNetwork: Boolean,
    @Value("#{'\${generator.ssrf.allowed-hosts:}'.split(',')}") private val allowedHosts: List<String>,
) : IngestSendClientPort {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    init {
        // 부팅 시 1회 SSRF 검증 — yml 의 targetUrl 이 외부 환경변수로 주입되는 경로(GENERATOR_TARGET)를 막는다.
        // 콤마 분리된 빈 문자열을 split 하면 [""] 가 나오므로 blank 제거.
        val sanitizedAllowed = allowedHosts.map { it.trim() }.filter { it.isNotEmpty() }
        SsrfGuard.validateUrl(
            url = targetUrl,
            allowedHosts = sanitizedAllowed,
            allowPrivateNetwork = allowPrivateNetwork,
        )
    }

    override suspend fun send(log: LogEvent): Boolean {
        return try {
            val response = httpClient.post(targetUrl) {
                contentType(ContentType.Application.Json)
                setBody(IngestSendRequest.from(log))
            }
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.error("Ingest 전송 실패 — transactionId: {}", log.transactionId, e)
            false
        }
    }
}
