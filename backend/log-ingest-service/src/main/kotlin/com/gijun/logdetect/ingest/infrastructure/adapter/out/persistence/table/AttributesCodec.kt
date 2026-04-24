package com.gijun.logdetect.ingest.infrastructure.adapter.out.persistence.table

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

// JSONB 컬럼 (attributes) 직렬화 — kotlinx.serialization Json
internal object AttributesCodec {
    private val json = Json { encodeDefaults = false }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    fun encode(value: Map<String, String>): String = json.encodeToString(serializer, value)

    fun decode(raw: String): Map<String, String> = json.decodeFromString(serializer, raw)
}
