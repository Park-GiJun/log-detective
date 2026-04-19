plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.20")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.3.20")
    implementation("org.jetbrains.kotlin:kotlin-noarg:2.3.20")
    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.5")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
    implementation("org.jetbrains.compose:compose-gradle-plugin:1.10.3")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.3.20")
    implementation("org.jlleitschuh.gradle:ktlint-gradle:12.1.2")
    implementation("dev.detekt:detekt-gradle-plugin:2.0.0-alpha.2")
    implementation("org.jetbrains.kotlin:kotlin-serialization:2.3.20")
}
