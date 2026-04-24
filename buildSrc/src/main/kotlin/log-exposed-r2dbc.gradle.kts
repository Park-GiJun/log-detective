// Exposed R2DBC + Flyway(JDBC) 공통 설정
// - 런타임 쿼리: Exposed R2DBC (suspend)
// - 마이그레이션: Flyway (JDBC) — R2DBC 미지원

plugins {
    kotlin("plugin.serialization")
}

dependencies {
    "implementation"(Dependencies.Exposed.CORE)
    "implementation"(Dependencies.Exposed.R2DBC)
    "implementation"(Dependencies.Exposed.JAVA_TIME)
    "implementation"(Dependencies.Exposed.JSON)

    // JSONB 직렬화 용
    "implementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.KOTLINX_SERIALIZATION}")

    "implementation"(Dependencies.R2dbc.POSTGRESQL)
    "implementation"(Dependencies.R2dbc.POOL)

    "implementation"(Dependencies.Spring.JDBC)
    "implementation"(Dependencies.Database.FLYWAY_CORE)
    "implementation"(Dependencies.Database.FLYWAY_POSTGRESQL)
    "runtimeOnly"(Dependencies.Database.POSTGRESQL)
}
