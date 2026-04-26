package com.gijun.logdetect.ingest.infrastructure.adapter.`in`.scheduler

import com.gijun.logdetect.ingest.application.port.`in`.command.DispatchOutboxUseCase
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Outbox 발행 스케줄러 (inbound adapter).
 *
 * WHY (이슈 #43): 헥사고날 컨벤션상 외부 트리거 (HTTP / Kafka consumer / scheduled task) 는
 * `infrastructure/adapter/in/...` 에 위치한다. `infrastructure/scheduler` 는 우리 컨벤션과 어긋나므로
 * `adapter/in/scheduler` 로 이동.
 *
 * 본 클래스는 정책을 보유하지 않는다 — `@Scheduled` 트리거에서 [DispatchOutboxUseCase] 를 호출만 한다.
 * 발행 정책은 [com.gijun.logdetect.ingest.application.handler.command.DispatchOutboxHandler] 가 보유.
 */
@Component
class OutboxScheduler(
    private val dispatchOutboxUseCase: DispatchOutboxUseCase,
) {

    @Scheduled(fixedDelayString = "\${logdetect.outbox.poll-interval-ms:1000}")
    fun pollAndDispatch() {
        dispatchOutboxUseCase.dispatchPending()
    }
}
