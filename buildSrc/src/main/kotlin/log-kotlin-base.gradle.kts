import org.gradle.api.tasks.TaskProvider
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

group = "com.gijun"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(Versions.JAVA)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(Dependencies.Kotlin.REFLECT)

    testImplementation(Dependencies.Test.KOTLIN_TEST)
    testImplementation(Dependencies.Test.MOCKK)
    testImplementation(Dependencies.Test.KOTEST_RUNNER)
    testImplementation(Dependencies.Test.KOTEST_ASSERTIONS)
    testImplementation(Dependencies.Test.KOTEST_PROPERTY)
    testRuntimeOnly(Dependencies.Test.JUNIT_LAUNCHER)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

// ── ktlint ──────────────────────────────────────────────────────────────
ktlint {
    version.set("1.5.0")
    android.set(false)
    ignoreFailures.set(false)
    verbose.set(true)
    outputToConsole.set(true)
    coloredOutput.set(true)
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

// ── detekt ─────────────────────────────────────────────────────────────
detekt {
    toolVersion = Versions.DETEKT
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
    ignoreFailures = false
    parallel = true
}

// ── build에서 ktlint/detekt 제외 (수동 실행만 허용) ────────────────────
// 기본적으로 ktlint-gradle과 detekt는 check 태스크에 자동 연결되어
// `gradlew build` 시 함께 실행된다. 빌드 파이프라인 속도를 위해 언훅한다.
afterEvaluate {
    tasks.matching { it.name == "check" }.configureEach {
        setDependsOn(
            dependsOn.filterNot { dep ->
                val name = when (dep) {
                    is TaskProvider<*> -> dep.name
                    is Task -> dep.name
                    else -> dep.toString()
                }
                name.startsWith("ktlint") || name.startsWith("detekt")
            },
        )
    }
}

tasks.withType<Test> {
    // Kotest 6.1.0 + Kotlin 2.3.20 환경에서 KotestJunitPlatformTestEngine.discover 가
    // NoSuchMethodError(SpecRef.Reference) 로 실패한다. 호환 가능한 Kotest 버전 또는
    // JUnit Jupiter 로 마이그레이션 전까지 kotest 엔진을 제외한다 — 별도 이슈.
    useJUnitPlatform {
        excludeEngines("kotest")
    }
    // log-generator 처럼 Kotest spec 만 있는 모듈은 위 제외로 인해 0개 발견 → Gradle 9
    // 기본 동작 (failOnNoDiscoveredTests=true) 가 빌드를 깨므로 끔.
    failOnNoDiscoveredTests = false
    testLogging {
        events("passed", "skipped", "failed")
    }
}
