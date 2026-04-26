package com.gijun.logdetect.ingest.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Outbox dispatch 전용 Executor.
 *
 * WHY — DispatchOutboxHandler 가 ES bulk 와 Kafka send 를 병렬 호출 (이슈 #93).
 *  ForkJoinPool.commonPool 을 쓰면 다른 컴포넌트의 병렬 stream / supplyAsync 와 경합하여
 *  outbox dispatch 의 latency 가 외부 요인으로 흔들린다. 전용 풀로 격리한다.
 *
 *  FixedThreadPool size 4 — 채널이 ES/Kafka 두 종류이므로 동시 작업은 2 개. 향후 채널 추가
 *  여유분 + 짧은 burst 흡수용으로 4 로 잡는다. 풀이 작아 풀 자체의 메모리/스레드 부담은 무시할 수준.
 */
@Configuration
class OutboxDispatchExecutorConfig {

    @Bean
    fun outboxDispatchExecutor(): Executor =
        Executors.newFixedThreadPool(POOL_SIZE, OutboxDispatchThreadFactory())

    private class OutboxDispatchThreadFactory : ThreadFactory {
        private val counter = AtomicInteger(1)
        override fun newThread(r: Runnable): Thread =
            Thread(r, "outbox-dispatch-${counter.getAndIncrement()}").apply {
                isDaemon = true
            }
    }

    companion object {
        private const val POOL_SIZE = 4
    }
}
