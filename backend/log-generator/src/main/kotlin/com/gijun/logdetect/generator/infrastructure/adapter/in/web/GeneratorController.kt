package com.gijun.logdetect.generator.infrastructure.adapter.`in`.web

import com.gijun.logdetect.generator.application.port.`in`.command.BurstGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.StartGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.StopGeneratorUseCase
import com.gijun.logdetect.generator.application.port.`in`.query.GetGeneratorStatusUseCase
import com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto.GeneratorStartRequest
import com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto.GeneratorStatusResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
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

    @GetMapping("/status")
    fun getStatus(): ResponseEntity<GeneratorStatusResponse> {
        val status = getGeneratorStatusUseCase.getGeneratorStatus()
        return ResponseEntity.ok(
            GeneratorStatusResponse(
                running = status.running,
                totalSent = status.totalSent,
                totalFailed = status.totalFailed,
                configuredRate = status.configuredRate,
            ),
        )
    }

    @PostMapping("/start")
    fun start(@Valid @RequestBody request: GeneratorStartRequest): ResponseEntity<Void> {
        startGeneratorUseCase.startGenerator(request.toCommand())
        return ResponseEntity.ok().build()
    }

    @PostMapping("/stop")
    fun stop(): ResponseEntity<Void> {
        stopGeneratorUseCase.stopGenerator()
        return ResponseEntity.ok().build()
    }

    @PostMapping("/burst")
    fun burst(@Valid @RequestBody request: GeneratorStartRequest): ResponseEntity<Void> {
        burstGeneratorUseCase.burstGenerator(request.toCommand())
        return ResponseEntity.ok().build()
    }
}
