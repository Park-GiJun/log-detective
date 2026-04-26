package com.gijun.logdetect.generator.infrastructure.adapter.out.client

import com.gijun.logdetect.generator.domain.model.LogEvent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.ByteReadChannel
import java.io.IOException
import java.time.Instant

private const val TARGET_URL = "http://ingest-test/api/v1/logs"
private const val TEST_API_KEY = "test-api-key"
private val ALLOWED_HOSTS = listOf("ingest-test")

class IngestSendClientAdapterTest : DescribeSpec({

    fun client(handler: MockRequestHandler): HttpClient {
        val engine = MockEngine(handler)
        return HttpClient(engine) {
            install(ContentNegotiation) { jackson() }
        }
    }

    fun newAdapter(httpClient: HttpClient, apiKey: String = TEST_API_KEY): IngestSendClientAdapter =
        IngestSendClientAdapter(
            httpClient = httpClient,
            targetUrl = TARGET_URL,
            allowPrivateNetwork = false,
            allowedHosts = ALLOWED_HOSTS,
            // 화이트리스트(`ingest-test`)에 매치되어 InetAddress 해석을 건너뛰므로 활성화해도 안전.
            perRequestValidation = true,
            apiKey = apiKey,
        )

    fun sampleEvent() = LogEvent(
        id = 1L,
        transactionId = "tx-123",
        source = "src",
        level = "INFO",
        message = "msg",
        timestamp = Instant.parse("2026-04-26T10:00:00Z"),
        host = "host-1",
        ip = "10.0.0.1",
        userId = 7L,
        attributes = mapOf("k" to "v"),
    )

    describe("HTTP 응답 코드별 분기") {
        it("200 OK → true 반환") {
            val httpClient = client {
                respond(
                    content = ByteReadChannel("{}"),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString())),
                )
            }
            val adapter = newAdapter(httpClient)
            adapter.send(sampleEvent()) shouldBe true
        }

        it("204 No Content 도 isSuccess() 이므로 true") {
            val httpClient = client {
                respond(content = "", status = HttpStatusCode.NoContent)
            }
            val adapter = newAdapter(httpClient)
            adapter.send(sampleEvent()) shouldBe true
        }

        it("400 Bad Request → false (isSuccess() false)") {
            val httpClient = client {
                respond(content = """{"error":"bad"}""", status = HttpStatusCode.BadRequest)
            }
            val adapter = newAdapter(httpClient)
            adapter.send(sampleEvent()) shouldBe false
        }

        it("500 Internal Server Error → false") {
            val httpClient = client {
                respondError(HttpStatusCode.InternalServerError)
            }
            val adapter = newAdapter(httpClient)
            adapter.send(sampleEvent()) shouldBe false
        }

        it("503 Service Unavailable → false") {
            val httpClient = client {
                respondError(HttpStatusCode.ServiceUnavailable)
            }
            val adapter = newAdapter(httpClient)
            adapter.send(sampleEvent()) shouldBe false
        }
    }

    describe("네트워크 예외") {
        it("HttpClient 가 IOException 던지면 catch 되어 false 반환 (예외 전파 안 됨)") {
            val httpClient = client {
                throw IOException("connection refused")
            }
            val adapter = newAdapter(httpClient)
            adapter.send(sampleEvent()) shouldBe false
        }

        it("RuntimeException 도 catch 되어 false") {
            val httpClient = client {
                throw RuntimeException("unexpected")
            }
            val adapter = newAdapter(httpClient)
            adapter.send(sampleEvent()) shouldBe false
        }
    }
})
