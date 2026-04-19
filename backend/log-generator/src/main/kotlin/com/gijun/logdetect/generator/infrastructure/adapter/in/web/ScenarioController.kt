package com.gijun.logdetect.generator.infrastructure.adapter.`in`.web

import com.gijun.logdetect.generator.application.dto.command.DeleteScenarioCommand
import com.gijun.logdetect.generator.application.dto.query.GetScenarioQuery
import com.gijun.logdetect.generator.application.port.`in`.command.CreateScenarioUseCase
import com.gijun.logdetect.generator.application.port.`in`.command.DeleteScenarioUseCase
import com.gijun.logdetect.generator.application.port.`in`.query.GetScenarioUseCase
import com.gijun.logdetect.generator.application.port.`in`.query.GetScenariosUseCase
import com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto.CreateScenarioRequest
import com.gijun.logdetect.generator.infrastructure.adapter.`in`.web.dto.ScenarioResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/scenarios")
class ScenarioController(
    private val createScenarioUseCase: CreateScenarioUseCase,
    private val deleteScenarioUseCase: DeleteScenarioUseCase,
    private val getScenarioUseCase: GetScenarioUseCase,
    private val getScenariosUseCase: GetScenariosUseCase,
) {

    @PostMapping
    fun create(@Valid @RequestBody request: CreateScenarioRequest): ResponseEntity<ScenarioResponse> {
        val result = createScenarioUseCase.createScenario(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(ScenarioResponse.from(result))
    }

    @GetMapping
    fun getAll(): ResponseEntity<List<ScenarioResponse>> {
        val results = getScenariosUseCase.getScenarios()
        return ResponseEntity.ok(results.map { ScenarioResponse.from(it) })
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<ScenarioResponse> {
        val result = getScenarioUseCase.getScenario(GetScenarioQuery(id))
        return ResponseEntity.ok(ScenarioResponse.from(result))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        deleteScenarioUseCase.deleteScenario(DeleteScenarioCommand(id))
        return ResponseEntity.noContent().build()
    }
}
