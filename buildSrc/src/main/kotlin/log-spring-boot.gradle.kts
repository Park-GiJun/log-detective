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
    implementation(Dependencies.Spring.WEB)
    implementation(Dependencies.Spring.ACTUATOR)
    implementation(Dependencies.Spring.VALIDATION)
    implementation(Dependencies.Spring.SECURITY)
    implementation(Dependencies.Jackson.KOTLIN_MODULE)
    implementation(Dependencies.SpringCloud.EUREKA_CLIENT)
    implementation(Dependencies.SpringCloud.CONFIG_CLIENT)
    implementation(Dependencies.SpringCloud.RESILIENCE4J)
    implementation(Dependencies.Observability.MICROMETER_PROMETHEUS)

    testImplementation(Dependencies.Test.SPRING_BOOT_TEST)
    testImplementation(Dependencies.Test.SPRING_SECURITY_TEST)
    testImplementation(Dependencies.Test.KOTEST_EXTENSIONS_SPRING)
    testImplementation(Dependencies.Test.COROUTINES_TEST)
}
