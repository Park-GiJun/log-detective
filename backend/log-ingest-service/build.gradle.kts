plugins {
    id("log-spring-boot-service")
    id("log-exposed-r2dbc")
}

dependencies {
    implementation(project(":log-common"))

    // Kafka
    implementation(Dependencies.Spring.KAFKA)

    // Elasticsearch (Reactive)
    implementation(Dependencies.Spring.ELASTICSEARCH)

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
    testImplementation(Dependencies.Test.SPRING_KAFKA_TEST)
    testImplementation(Dependencies.Test.KTOR_CLIENT_MOCK)
}
