plugins {
    id("log-spring-boot-service")
    id("log-exposed-r2dbc")
}

dependencies {
    implementation(project(":log-common"))

    // Coroutines (Generator 내부 동시 전송용)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Kafka (메시지 전송용)
    implementation(Dependencies.Spring.KAFKA)

    // Redisson (Generator 상태 관리)
    implementation(Dependencies.Redisson.SPRING_BOOT_STARTER)

    // Ktor Client (로그 주입용)
    implementation(Dependencies.Ktor.CLIENT_CORE)
    implementation(Dependencies.Ktor.CLIENT_CIO)
    implementation(Dependencies.Ktor.CLIENT_CONTENT_NEGOTIATION)
    implementation(Dependencies.Ktor.SERIALIZATION_JACKSON)
    implementation(Dependencies.Ktor.CLIENT_LOGGING)

    // Test — Ktor Client Mock
    testImplementation(Dependencies.Test.KTOR_CLIENT_MOCK)
}
