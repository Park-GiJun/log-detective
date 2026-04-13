import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

group = "com.gijun"
version = "0.0.1-SNAPSHOT"

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("compose-web")
        browser {
            commonWebpackConfig {
                outputFileName = "compose-web.js"
                devServer =
                    (
                        devServer ?: org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpackConfig
                            .DevServer()
                    ).apply {
                        open = false
                        port = 3003
                    }
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.components.resources)
            }
        }
    }
}

// ── ktlint ─────────────────────────────────────────────────────────────
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

// ── detekt ────────────────────────────────────────────────────────────
detekt {
    toolVersion = Versions.DETEKT
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    autoCorrect = false
    ignoreFailures = false
    parallel = true
}

// ── build 시엔 실행 안 함 (수동 실행만) ─────────────────────────────────
afterEvaluate {
    tasks.matching { it.name == "check" }.configureEach {
        setDependsOn(
            dependsOn.filterNot { dep ->
                val name =
                    when (dep) {
                        is TaskProvider<*> -> dep.name
                        is Task -> dep.name
                        else -> dep.toString()
                    }
                name.startsWith("ktlint") || name.startsWith("detekt")
            },
        )
    }
}
