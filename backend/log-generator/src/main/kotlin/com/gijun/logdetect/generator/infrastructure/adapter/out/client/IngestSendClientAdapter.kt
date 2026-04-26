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
    @Value("\${generator.ssrf.per-request-validation:true}") private val perRequestValidation: Boolean,
) : IngestSendClientPort {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    // 콤마 분리된 빈 문자열을 split 하면 [""] 가 나오므로 blank 제거.
    private val sanitizedAllowedHosts: List<String> = allowedHosts.map { it.trim() }.filter { it.isNotEmpty() }

    init {
        // 부팅 시 1회 SSRF 검증 — yml 의 targetUrl 이 외부 환경변수로 주입되는 경로(GENERATOR_TARGET)를 막는다.
        // 부팅 검증은 빠른 fail-fast 이고, 실제 send 시 per-request 재검증으로 DNS rebinding 을 방어한다 (이슈 #99).
        SsrfGuard.validateUrl(
            url = targetUrl,
            allowedHosts = sanitizedAllowedHosts,
            allowPrivateNetwork = allowPrivateNetwork,
        )
    }

    override suspend fun send(log: LogEvent): Boolean {
        // TOCTOU 보호 — 부팅 검증 이후 DNS 재해석 결과가 바뀌어 사설망/메타데이터 IP 로 해석될 수 있다.
        // per-request 재검증으로 매 요청 직전 다시 한 번 호스트 → IP 해석을 검사한다 (이슈 #99).
        // 운영 부하가 우려되면 `generator.ssrf.per-request-validation=false` 로 비활성화 가능.
        if (perRequestValidation) {
            try {
                SsrfGuard.validateUrl(
                    url = targetUrl,
                    allowedHosts = sanitizedAllowedHosts,
                    allowPrivateNetwork = allowPrivateNetwork,
                )
            } catch (e: SsrfViolationException) {
                logger.error("SSRF per-request 재검증 실패 — transactionId: {}, reason: {}", log.transactionId, e.message)
                return false
            }
        }

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
