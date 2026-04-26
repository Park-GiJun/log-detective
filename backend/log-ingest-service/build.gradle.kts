plugins {
    id("log-spring-boot-service")
    kotlin("plugin.jpa")
}

dependencies {
    implementation(project(":log-common"))

    // Kafka
    implementation(Dependencies.Spring.KAFKA)

    // JPA + Flyway
    implementation(Dependencies.Spring.JPA)
    implementation(Dependencies.Spring.FLYWAY)
    implementation(Dependencies.Database.FLYWAY_POSTGRESQL)
    runtimeOnly(Dependencies.Database.POSTGRESQL)

    // Elasticsearch
    implementation(Dependencies.Spring.ELASTICSEARCH)

    // Caffeine — 인증 실패 로그 IP 별 rate-limit 캐시 (1초 1회 로그 spam 방지, 메모리 폭증 차단)
    implementation(Dependencies.Cache.CAFFEINE)
    // Redisson (이슈 #110 — brute-force 보안 lockout 카운터/락아웃 분산 캐시)
    implementation(Dependencies.Redisson.SPRING_BOOT_STARTER)
    // Micrometer Prometheus 는 log-spring-boot 공통에서 이미 포함.

    // Ktor Client
    implementation(Dependencies.Ktor.CLIENT_CORE)
    implementation(Dependencies.Ktor.CLIENT_CIO)
    implementation(Dependencies.Ktor.CLIENT_CONTENT_NEGOTIATION)
    implementation(Dependencies.Ktor.SERIALIZATION_JACKSON)
    implementation(Dependencies.Ktor.CLIENT_LOGGING)

    // Test — Spring Boot Test + Testcontainers + Kafka
    testImplementation(Dependencies.Test.SPRING_BOOT_TEST)
    testImplementation(platform(Dependencies.Test.TESTCONTAINERS_BOM))
    testImplementation(Dependencies.Test.TESTCONTAINERS_JUNIT)
    testImplementation(Dependencies.Test.TESTCONTAINERS_POSTGRESQL)
    testImplementation(Dependencies.Test.TESTCONTAINERS_KAFKA)
    testImplementation(Dependencies.Test.TESTCONTAINERS_ELASTICSEARCH)
    testImplementation(Dependencies.Test.SPRING_KAFKA_TEST)
    testImplementation(Dependencies.Test.KTOR_CLIENT_MOCK)
}
