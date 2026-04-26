package com.gijun.logdetect.generator.application.handler.command

import com.gijun.logdetect.generator.application.dto.command.GeneratorStartCommand
import com.gijun.logdetect.generator.application.port.out.GeneratorStateCachePort
import com.gijun.logdetect.generator.application.port.out.IngestSendClientPort
import com.gijun.logdetect.generator.application.port.out.IngestSendFilePort
import com.gijun.logdetect.generator.application.port.out.IngestSendMessagePort
import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort
import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType
import com.gijun.logdetect.generator.domain.model.Scenario
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

private const val ASYNC_TIMEOUT_MS = 3_000L

class GeneratorCommandHandlerTest : DescribeSpec({

    fun newHandler(
        scenarioPort: ScenarioPersistencePort = mockk(),
        clientPort: IngestSendClientPort = mockk(),
        messagePort: IngestSendMessagePort = mockk(),
        filePort: IngestSendFilePort = mockk(),
        cachePort: GeneratorStateCachePort = mockk(relaxed = true),
    ) = GeneratorCommandHandler(scenarioPort, clientPort, messagePort, filePort, cachePort)

    fun scenario(
        id: Long = 1L,
        type: RequestType = RequestType.REST,
        rate: Long = 1L,
        fraudRatio: Long = 100L, // 100% — 매번 suspicious 가 생성되어 send 가 호출됨
    ) = Scenario(
        id = id,
        name = "test",
        type = type,
        attackType = AttackType.BRUTE_FORCE,
        successful = false,
        rate = rate,
        fraudRatio = fraudRatio,
    )

    describe("startGenerator") {
        it("시나리오를 찾을 수 없으면 IllegalArgumentException") {
            val scenarioPort = mockk<ScenarioPersistencePort>()
            every { scenarioPort.findById(999L) } returns null
            val handler = newHandler(scenarioPort = scenarioPort)

            shouldThrow<IllegalArgumentException> {
                handler.startGenerator(GeneratorStartCommand(scenarioId = 999L))
            }
        }

        it("동일 시나리오 두 번째 start 호출은 무시된다 (이중 실행 방지)") {
            val scenarioPort = mockk<ScenarioPersistencePort>()
            val clientPort = mockk<IngestSendClientPort>()
            every { scenarioPort.findById(1L) } returns scenario()
            coEvery { clientPort.send(any()) } returns true
            val handler = newHandler(scenarioPort = scenarioPort, clientPort = clientPort)

            handler.startGenerator(GeneratorStartCommand(scenarioId = 1L))
            handler.startGenerator(GeneratorStartCommand(scenarioId = 1L))

            handler.isRunning(1L).shouldBeTrue()
            handler.stopGenerator(1L)
        }

        it("서로 다른 시나리오는 동시에 실행 가능") {
            val scenarioPort = mockk<ScenarioPersistencePort>()
            val clientPort = mockk<IngestSendClientPort>()
            every { scenarioPort.findById(1L) } returns scenario(id = 1L)
            every { scenarioPort.findById(2L) } returns scenario(id = 2L)
            coEvery { clientPort.send(any()) } returns true
            val handler = newHandler(scenarioPort = scenarioPort, clientPort = clientPort)

            handler.startGenerator(GeneratorStartCommand(scenarioId = 1L))
            handler.startGenerator(GeneratorStartCommand(scenarioId = 2L))

            handler.isRunning(1L).shouldBeTrue()
            handler.isRunning(2L).shouldBeTrue()
            handler.stopAll()
        }
    }

    describe("burstGenerator — RequestType 분기") {
        it("REST → IngestSendClientPort.send 만 호출") {
            val scenarioPort = mockk<ScenarioPersistencePort>()
            val clientPort = mockk<IngestSendClientPort>()
            val messagePort = mockk<IngestSendMessagePort>()
            val filePort = mockk<IngestSendFilePort>()
            every { scenarioPort.findById(1L) } returns scenario(type = RequestType.REST)
            coEvery { clientPort.send(any()) } returns true
            val handler = newHandler(scenarioPort, clientPort, messagePort, filePort)

            handler.burstGenerator(GeneratorStartCommand(scenarioId = 1L))

            coVerify(timeout = ASYNC_TIMEOUT_MS) { clientPort.send(any()) }
            coVerify(exactly = 0) { messagePort.send(any()) }
            coVerify(exactly = 0) { filePort.send(any()) }
        }

        it("KAFKA → IngestSendMessagePort.send 만 호출") {
            val scenarioPort = mockk<ScenarioPersistencePort>()
            val clientPort = mockk<IngestSendClientPort>()
            val messagePort = mockk<IngestSendMessagePort>()
            val filePort = mockk<IngestSendFilePort>()
            every { scenarioPort.findById(1L) } returns scenario(type = RequestType.KAFKA)
            coEvery { messagePort.send(any()) } returns true
            val handler = newHandler(scenarioPort, clientPort, messagePort, filePort)

            handler.burstGenerator(GeneratorStartCommand(scenarioId = 1L))

            coVerify(timeout = ASYNC_TIMEOUT_MS) { messagePort.send(any()) }
            coVerify(exactly = 0) { clientPort.send(any()) }
            coVerify(exactly = 0) { filePort.send(any()) }
        }

        it("FILE → IngestSendFilePort.send 만 호출") {
            val scenarioPort = mockk<ScenarioPersistencePort>()
            val clientPort = mockk<IngestSendClientPort>()
            val messagePort = mockk<IngestSendMessagePort>()
            val filePort = mockk<IngestSendFilePort>()
            every { scenarioPort.findById(1L) } returns scenario(type = RequestType.FILE)
            coEvery { filePort.send(any()) } returns true
            val handler = newHandler(scenarioPort, clientPort, messagePort, filePort)

            handler.burstGenerator(GeneratorStartCommand(scenarioId = 1L))

            coVerify(timeout = ASYNC_TIMEOUT_MS) { filePort.send(any()) }
            coVerify(exactly = 0) { clientPort.send(any()) }
            coVerify(exactly = 0) { messagePort.send(any()) }
        }
    }

    describe("stopGenerator") {
        it("실행되지 않은 시나리오 stop 은 무시되며 예외 없음") {
            val handler = newHandler()
            handler.stopGenerator(999L)
        }
    }
})

