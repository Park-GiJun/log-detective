package com.gijun.logdetect.generator.application.port.`in`.command

interface StopGeneratorUseCase {

    /** 단일 시나리오 정지. 이미 정지/미실행 상태면 무시. */
    fun stopGenerator(scenarioId: Long)

    /** 실행 중인 모든 시나리오 정지. */
    fun stopAll()
}
