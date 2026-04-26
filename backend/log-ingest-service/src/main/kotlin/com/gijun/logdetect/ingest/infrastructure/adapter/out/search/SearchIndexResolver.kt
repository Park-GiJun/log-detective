package com.gijun.logdetect.ingest.infrastructure.adapter.out.search

import com.gijun.logdetect.ingest.application.port.out.SearchIndexResolverPort
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * ES 인덱스 명명 규칙 `logs-yyyy.MM.dd` (UTC) 의 단일 책임자.
 *
 * WHY — 명명 규칙이 한 곳에만 존재해야 일/월 단위 롤오버 / ILM 정책 변경 시 여기만 고치면 된다.
 */
@Component
class SearchIndexResolver : SearchIndexResolverPort {

    override fun resolveIndexName(timestamp: Instant): String {
        val date = timestamp.atOffset(ZoneOffset.UTC).format(DATE_FORMAT)
        return "$INDEX_PREFIX$date"
    }

    companion object {
        private const val INDEX_PREFIX = "logs-"
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd")
    }
}
