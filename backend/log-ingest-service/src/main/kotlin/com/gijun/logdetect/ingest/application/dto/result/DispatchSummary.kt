package com.gijun.logdetect.ingest.application.dto.result

/**
 * 한 사이클의 Outbox dispatch 결과 요약.
 *
 * @property total fetchPending 으로 가져온 총 행 수.
 * @property succeeded 채널 발행 성공 + markPublished 처리된 행 수.
 * @property failed 외부 IO 실패로 markFailed 된 행 수 (재시도 대기).
 * @property dead MAX_ATTEMPTS 도달 또는 즉시 dead (미지원 채널 등) 처리된 행 수.
 */
data class DispatchSummary(
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val dead: Int,
) {
    companion object {
        val EMPTY = DispatchSummary(0, 0, 0, 0)
    }
}
