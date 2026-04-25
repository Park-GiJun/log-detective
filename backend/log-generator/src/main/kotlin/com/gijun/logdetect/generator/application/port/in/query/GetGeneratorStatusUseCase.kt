package com.gijun.logdetect.generator.application.port.`in`.query

import com.gijun.logdetect.generator.domain.model.GeneratorStatus

interface GetGeneratorStatusUseCase {

    /** 시나리오별 상태 — 실행 중이거나 과거 카운터가 남아 있는 모든 시나리오. */
    fun getGeneratorStatuses(): List<GeneratorStatus>
}
