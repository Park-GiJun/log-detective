pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

rootProject.name = "log-detective"

// ── Backend ──
include(
    "log-common",
    "log-eureka-server",
    "log-gateway",
    "log-ingest-service",
    "log-detection-service",
    "log-alert-service",
    "log-generator",
)
project(":log-common").projectDir = file("backend/log-common")
project(":log-eureka-server").projectDir = file("backend/log-eureka-server")
project(":log-gateway").projectDir = file("backend/log-gateway")
project(":log-ingest-service").projectDir = file("backend/log-ingest-service")
project(":log-detection-service").projectDir = file("backend/log-detection-service")
project(":log-alert-service").projectDir = file("backend/log-alert-service")
project(":log-generator").projectDir = file("backend/log-generator")

// ── Frontend ──
include("compose-web")
project(":compose-web").projectDir = file("frontend/compose-web")
