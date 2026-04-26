package com.gijun.logdetect.ingest.domain.model

import com.gijun.logdetect.common.domain.model.LogEvent
import java.time.Instant

/**
 * 영속화된 LogEvent 와 ingestedAt 타임스탬프를 함께 표현하는 VO.
 * Pair<LogEvent, Instant> 의 의미 모호성을 제거하기 위해 도입.
 */
data class IngestedLogEvent(
    val event: LogEvent,
    val ingestedAt: Instant,
)
