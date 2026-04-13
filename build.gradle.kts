// 플러그인은 buildSrc에서 관리하므로 루트에서는 선언하지 않음.
// 전체 모듈에 대한 코드 품질 집계 태스크만 여기서 제공한다.

tasks.register("codeQuality") {
    group = "verification"
    description = "모든 서브프로젝트에 대해 ktlintCheck + detekt 를 실행한다"
    dependsOn(
        subprojects.mapNotNull { it.tasks.findByName("ktlintCheck") },
        subprojects.mapNotNull { it.tasks.findByName("detekt") },
    )
}

tasks.register("codeFormat") {
    group = "formatting"
    description = "모든 서브프로젝트에 대해 ktlintFormat 을 실행한다"
    dependsOn(subprojects.mapNotNull { it.tasks.findByName("ktlintFormat") })
}
