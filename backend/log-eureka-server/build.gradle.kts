plugins {
    id("log-kotlin-base")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom(Dependencies.SpringCloud.BOM)
    }
}

dependencies {
    implementation(Dependencies.Spring.WEB)
    implementation(Dependencies.Spring.ACTUATOR)
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server")
    implementation(Dependencies.SpringCloud.CONFIG_SERVER)
    implementation(Dependencies.Spring.SECURITY)

    testImplementation(Dependencies.Test.SPRING_BOOT_TEST)
}
