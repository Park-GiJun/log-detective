package com.gijun.logdetect.ingest.domain.port

import java.time.Instant

/**
 * 도메인 시간 추상화 — 도메인 outbound port.
 *
 * WHY — `Instant.now()` 직접 호출은 (1) 단위 테스트에서 시간을 제어할 수 없게 하고,
 * (2) 도메인 / 핸들러 / 어댑터 어디서나 호출이 흩어져 시계 변경 정책 (UTC 고정 등) 의
 * 단일 진입점을 만들기 어렵다. 도메인 전용 포트로 한 번 감싸서 인프라/테스트가 주입하도록 한다.
 *
 * 위치 — 다른 outbound port (`*Persistence`, `*Search`, `*Message`) 와 동일하게
 * `domain/port` 하위에 둔다 (이슈 #99). 이전에는 `domain/Clock.kt` 에 단독 배치되었으나,
 * 도메인이 의존하는 모든 추상은 한 디렉터리(`port`)로 모아 응집도를 높였다.
 *
 * `java.time.Clock` 을 굳이 쓰지 않은 이유 — 도메인 패키지가 java.time 외 추가 API surface 를
 * 노출하지 않게 하고, `now()` 한 메서드만으로 의미를 단순화하려는 의도.
 */
fun interface Clock {
    fun now(): Instant
}
