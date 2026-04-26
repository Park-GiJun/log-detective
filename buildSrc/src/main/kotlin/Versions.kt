object Versions {
    const val JAVA = 25
    const val SPRING_CLOUD = "2025.1.1"
    const val SPRINGDOC = "3.0.2"
    const val KTOR = "3.4.0"
    const val EXPOSED = "1.1.1"
    const val CAFFEINE = "3.2.0"
    const val REDISSON = "3.52.0"

    // Frontend / Kotlin
    const val KOTLIN = "2.3.20"
    const val COMPOSE_MULTIPLATFORM = "1.10.3"
    const val KOTLINX_SERIALIZATION = "1.8.1"
    const val KOTLINX_COROUTINES = "1.10.2"

    // Test
    // Kotest 6.1.0 은 Kotlin 2.3.20 환경에서 KotestJunitPlatformTestEngine.discover 가
    // NoSuchMethodError(SpecRef.Reference) 로 실패. 6.0.4 maintenance 라인으로 다운그레이드 — Refs #70
    const val KOTEST = "6.0.4"
    const val MOCKK = "1.14.2"
    const val TESTCONTAINERS = "1.21.0"

    // Quality
    const val KTLINT_GRADLE = "12.1.2"
    const val DETEKT = "2.0.0-alpha.2"
}
