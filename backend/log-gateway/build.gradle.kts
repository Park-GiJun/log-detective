plugins {
    id("log-spring-boot")
}

dependencies {
    implementation(Dependencies.SpringCloud.GATEWAY)
    implementation(Dependencies.Cache.CAFFEINE)
}
