plugins {
    id("log-spring-boot-service")
}

dependencies {
    implementation(project(":log-common"))

    // Coroutines (Generator 내부 동시 전송용)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Ktor Client (로그 주입용)
    implementation(Dependencies.Ktor.CLIENT_CORE)
    implementation(Dependencies.Ktor.CLIENT_CIO)
    implementation(Dependencies.Ktor.CLIENT_CONTENT_NEGOTIATION)
    implementation(Dependencies.Ktor.SERIALIZATION_JACKSON)
    implementation(Dependencies.Ktor.CLIENT_LOGGING)

    // Test — Ktor Client Mock
    testImplementation(Dependencies.Test.KTOR_CLIENT_MOCK)
}
