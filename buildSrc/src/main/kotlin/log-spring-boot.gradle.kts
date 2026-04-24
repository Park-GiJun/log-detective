plugins {
    id("log-kotlin-base")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

// Spring Boot 4.0.x는 Kotlin 2.2.21을 기본 관리하므로, 2.3.20으로 강제 오버라이드
extra["kotlin.version"] = Versions.KOTLIN

dependencyManagement {
    imports {
        mavenBom(Dependencies.SpringCloud.BOM)
    }
}

dependencies {
    implementation(Dependencies.Spring.WEBFLUX)
    implementation(Dependencies.Spring.ACTUATOR)
    implementation(Dependencies.Spring.VALIDATION)
    implementation(Dependencies.Spring.SECURITY)
    implementation(Dependencies.Jackson.KOTLIN_MODULE)
    implementation(Dependencies.SpringCloud.EUREKA_CLIENT)
    implementation(Dependencies.SpringCloud.CONFIG_CLIENT)
    implementation(Dependencies.SpringCloud.RESILIENCE4J_REACTOR)
    implementation(Dependencies.Observability.MICROMETER_PROMETHEUS)

    // 코루틴 Reactor 브리지 — suspend 함수 ↔ Mono/Flux 변환
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    testImplementation(Dependencies.Test.SPRING_BOOT_TEST)
    testImplementation(Dependencies.Test.SPRING_SECURITY_TEST)
    testImplementation(Dependencies.Test.KOTEST_EXTENSIONS_SPRING)
    testImplementation(Dependencies.Test.COROUTINES_TEST)
}
