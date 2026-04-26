package com.gijun.logdetect.ingest.application.port.`in`.command

import com.gijun.logdetect.ingest.application.dto.result.DispatchSummary

/**
 * Outbox 발행 유스케이스.
 *
 * WHY — 발행 정책 (백오프, MAX_ATTEMPTS, 채널별 dispatch) 은 application 계층의 책임.
 * 스케줄러는 단순히 트리거이고, 본 use case 가 정책을 보유한다 (이슈 #44).
 */
interface DispatchOutboxUseCase {

    /**
     * PENDING / 만료된 FAILED 행을 한 batch 가져와 채널별로 발행한다.
     *
     * 트랜잭션 분리 (이슈 #25):
     * - fetchPending 과 mark* 만 짧은 트랜잭션. 외부 IO 는 트랜잭션 밖.
     */
    fun dispatchPending(): DispatchSummary
}
