package com.gijun.logdetect.generator.infrastructure.adapter.`in`.web

import com.gijun.logdetect.generator.application.port.`in`.command.BurstGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.StartGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.StopGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.query.GetGeneratorStatusUseCase
import com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto.GeneratorStartRequest
import com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto.GeneratorStatusResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/generator")
class GeneratorController(
    private val startGeneratorUseCase: StartGeneratorUseCase,
    private val stopGeneratorUseCase: StopGeneratorUseCase,
    private val burstGeneratorUseCase: BurstGeneratorUseCase,
    private val getGeneratorStatusUseCase: GetGeneratorStatusUseCase,
) {

    /** 실행 중이거나 카운터가 남아있는 모든 시나리오의 상태. */
    @GetMapping("/status")
    fun getStatuses(): ResponseEntity<List<GeneratorStatusResponse>> {
        val statuses = getGeneratorStatusUseCase.getGeneratorStatuses()
        return ResponseEntity.ok(statuses.map { GeneratorStatusResponse.from(it) })
    }

    @PostMapping("/start")
    fun start(@Valid @RequestBody request: GeneratorStartRequest): ResponseEntity<Void> {
        startGeneratorUseCase.startGenerator(request.toCommand())
        return ResponseEntity.ok().build()
    }

    /** 단일 시나리오 정지. */
    @DeleteMapping("/stop/{scenarioId}")
    fun stop(@PathVariable scenarioId: Long): ResponseEntity<Void> {
        stopGeneratorUseCase.stopGenerator(scenarioId)
        return ResponseEntity.ok().build()
    }

    /** 전체 시나리오 정지. */
    @DeleteMapping("/stop-all")
    fun stopAll(): ResponseEntity<Void> {
        stopGeneratorUseCase.stopAll()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/burst")
    fun burst(@Valid @RequestBody request: GeneratorStartRequest): ResponseEntity<Void> {
        burstGeneratorUseCase.burstGenerator(request.toCommand())
        return ResponseEntity.ok().build()
    }
}
