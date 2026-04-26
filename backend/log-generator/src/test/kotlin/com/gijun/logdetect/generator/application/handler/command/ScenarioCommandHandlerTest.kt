package com.gijun.logdetect.generator.application.handler.command

import com.gijun.logdetect.generator.application.dto.command.CreateScenarioCommand
import com.gijun.logdetect.generator.application.dto.command.DeleteScenarioCommand
import com.gijun.logdetect.generator.application.port.out.ScenarioPersistencePort
import com.gijun.logdetect.generator.domain.enums.AttackType
import com.gijun.logdetect.generator.domain.enums.RequestType
import com.gijun.logdetect.generator.domain.model.Scenario
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class ScenarioCommandHandlerTest : DescribeSpec({

    fun handler(port: ScenarioPersistencePort = mockk()) = ScenarioCommandHandler(port)

    fun createCommand() = CreateScenarioCommand(
        name = "brute-force-1",
        type = RequestType.REST,
        attackType = AttackType.BRUTE_FORCE,
        successful = false,
        rate = 100L,
        fraudRatio = 50L,
    )

    fun savedScenario(id: Long = 1L) = Scenario(
        id = id,
        name = "brute-force-1",
        type = RequestType.REST,
        attackType = AttackType.BRUTE_FORCE,
        successful = false,
        rate = 100L,
        fraudRatio = 50L,
    )

    describe("createScenario") {
        it("Command 의 모든 필드가 Scenario 도메인 모델에 그대로 매핑되어 저장되고 ScenarioResult 로 반환된다") {
            val port = mockk<ScenarioPersistencePort>()
            val captured = slot<Scenario>()
            every { port.save(capture(captured)) } returns savedScenario()
            val h = handler(port)

            val result = h.createScenario(createCommand())

            captured.captured.id shouldBe null
            captured.captured.name shouldBe "brute-force-1"
            captured.captured.type shouldBe RequestType.REST
            captured.captured.attackType shouldBe AttackType.BRUTE_FORCE
            captured.captured.fraudRatio shouldBe 50L
            result.id shouldBe 1L
            result.name shouldBe "brute-force-1"
        }
    }

    describe("deleteScenario") {
        it("존재하는 시나리오 → findById 후 deleteById 가 호출된다") {
            val port = mockk<ScenarioPersistencePort>()
            every { port.findById(1L) } returns savedScenario(1L)
            every { port.deleteById(1L) } just Runs
            val h = handler(port)

            h.deleteScenario(DeleteScenarioCommand(id = 1L))

            verify(exactly = 1) { port.findById(1L) }
            verify(exactly = 1) { port.deleteById(1L) }
        }

        it("존재하지 않는 시나리오 → IllegalArgumentException + deleteById 호출 없음 (findById null 가드)") {
            val port = mockk<ScenarioPersistencePort>()
            every { port.findById(999L) } returns null
            val h = handler(port)

            shouldThrow<IllegalArgumentException> {
                h.deleteScenario(DeleteScenarioCommand(id = 999L))
            }
            verify(exactly = 0) { port.deleteById(any()) }
        }

        it("delete 레이스 — findById 통과 후 다른 트랜잭션이 먼저 지웠다면 deleteById 가 그대로 호출되며 idempotent 처리는 영속 계층 책임이다") {
            // findById 시점에는 row 가 있으나 deleteById 시점에 이미 삭제됐다고 가정.
            // Handler 책임은 findById 가드까지 — 영속 계층의 deleteById 가 멱등하면 예외 없음.
            val port = mockk<ScenarioPersistencePort>()
            every { port.findById(7L) } returns savedScenario(7L)
            every { port.deleteById(7L) } just Runs // 멱등 가정
            val h = handler(port)

            h.deleteScenario(DeleteScenarioCommand(id = 7L))

            verify(exactly = 1) { port.deleteById(7L) }
        }
    }
})
