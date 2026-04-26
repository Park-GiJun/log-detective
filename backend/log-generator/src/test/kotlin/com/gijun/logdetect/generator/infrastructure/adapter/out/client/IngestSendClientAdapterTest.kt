package com.gijun.logdetect.generator.infrastructure.adapter.out.client

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.gijun.logdetect.generator.domain.model.LogEvent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.jackson.jackson
import io.ktor.utils.io.ByteReadChannel
import org.slf4j.LoggerFactory
import java.io.IOException
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

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

    // 이슈 #108 — 회귀 방어선: header(API_KEY_HEADER, apiKey) 라인을 통째 삭제해도
    // 이전 테스트는 PASS 했다. MockEngine 핸들러에서 X-API-Key 를 직접 캡처해 검증한다.
    describe("X-API-Key 헤더 첨부 동작") {
        // MockEngine 핸들러는 호출 시점에 HttpRequestData 를 받는다. AtomicReference 로 캡처해
        // 응답 처리 이후 단언에서 헤더 값을 꺼낸다.
        fun captureClient(captured: AtomicReference<HttpRequestData?>): HttpClient =
            client { request ->
                captured.set(request)
                respond(
                    content = "",
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString())),
                )
            }

        it("apiKey='test-api-key' 호출 시 X-API-Key 헤더가 정확히 첨부된다") {
            val captured = AtomicReference<HttpRequestData?>(null)
            val adapter = newAdapter(captureClient(captured), apiKey = TEST_API_KEY)

            adapter.send(sampleEvent()) shouldBe true

            val request = captured.get()!!
            request.headers[IngestSendClientAdapter.API_KEY_HEADER] shouldBe TEST_API_KEY
        }

        it("apiKey='another-secret-9k!' 같은 임의 값도 그대로 헤더에 전달된다") {
            val captured = AtomicReference<HttpRequestData?>(null)
            val arbitraryKey = "another-secret-9k!"
            val adapter = newAdapter(captureClient(captured), apiKey = arbitraryKey)

            adapter.send(sampleEvent()) shouldBe true

            captured.get()!!.headers[IngestSendClientAdapter.API_KEY_HEADER] shouldBe arbitraryKey
        }

        it("apiKey='' (blank) 인 경우 X-API-Key 헤더가 첨부되지 않는다 — 빈 환경에서 401 회귀 방어") {
            val captured = AtomicReference<HttpRequestData?>(null)
            val adapter = newAdapter(captureClient(captured), apiKey = "")

            adapter.send(sampleEvent()) shouldBe true

            // contains 가 아니라 null 단언으로 — 빈 문자열이 잘못 전달되는 회귀(`X-API-Key=""`)도 차단.
            captured.get()!!.headers[IngestSendClientAdapter.API_KEY_HEADER] shouldBe null
        }

        it("apiKey='   ' (공백뿐) 도 isBlank() 분기로 헤더 미첨부") {
            val captured = AtomicReference<HttpRequestData?>(null)
            val adapter = newAdapter(captureClient(captured), apiKey = "   ")

            adapter.send(sampleEvent()) shouldBe true

            captured.get()!!.headers[IngestSendClientAdapter.API_KEY_HEADER] shouldBe null
        }
    }

    // 이슈 #108 — apiKey 가 비어있을 때 init 블록의 warn 로그가 실제로 찍히는지 회귀 방어.
    // logback ListAppender 로 IngestSendClientAdapter 로거에 부착해 캡처한다.
    describe("apiKey blank 경고 로그") {
        fun attachAppender(): ListAppender<ILoggingEvent> {
            val logger = LoggerFactory.getLogger(IngestSendClientAdapter::class.java) as Logger
            val appender = ListAppender<ILoggingEvent>().apply { start() }
            logger.addAppender(appender)
            return appender
        }

        fun detach(appender: ListAppender<ILoggingEvent>) {
            val logger = LoggerFactory.getLogger(IngestSendClientAdapter::class.java) as Logger
            logger.detachAppender(appender)
            appender.stop()
        }

        it("apiKey='' 로 부팅하면 WARN 로그에 INGEST_API_KEY 안내 메시지가 남는다") {
            val appender = attachAppender()
            try {
                newAdapter(client { respond(content = "", status = HttpStatusCode.OK) }, apiKey = "")
                val warnMessages = appender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
                warnMessages.any { it.contains("INGEST_API_KEY") } shouldBe true
            } finally {
                detach(appender)
            }
        }

        it("apiKey 가 정상 값이면 WARN 로그가 남지 않는다") {
            val appender = attachAppender()
            try {
                newAdapter(client { respond(content = "", status = HttpStatusCode.OK) }, apiKey = TEST_API_KEY)
                appender.list.filter { it.level == Level.WARN }
                    .map { it.formattedMessage } shouldBe emptyList()
            } finally {
                detach(appender)
            }
        }
    }
})
