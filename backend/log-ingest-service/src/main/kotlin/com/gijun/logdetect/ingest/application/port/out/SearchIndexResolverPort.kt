package com.gijun.logdetect.ingest.application.port.out

import java.time.Instant

/**
 * ES 인덱스명 결정 포트.
 *
 * WHY — `logs-yyyy.MM.dd` 같은 인덱스 명명 규칙은 ES 운영 정책이지 도메인의 관심사가 아니다.
 * 핸들러가 직접 [java.time.format.DateTimeFormatter] 로 포매팅하면 인프라 디테일이
 * application 계층으로 새는데, 포트로 격리하면 호출만 해서 destination 을 받는다.
 */
fun interface SearchIndexResolverPort {

    /**
     * 이벤트 timestamp 가 속한 ES 인덱스명을 결정한다.
     */
    fun resolveIndexName(timestamp: Instant): String
}
