plugins {
    application
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":model"))
    implementation(libs.jsoup)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass = "org.nowstart.kradmincodes.pipeline.PipelineApp"
}

tasks.named<JavaExec>("run") {
    // data/, overrides/ 상대 경로가 레포 루트 기준이 되도록
    workingDir = rootProject.projectDir
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// 네트워크·CLI 진입점은 단위 테스트 대신 실제 실행으로 확인한다
val coverageExcludes = listOf(
    "**/pipeline/PipelineApp*",
    "**/pipeline/source/HttpFetcher*",
    "**/pipeline/source/MoisBoardClient*",
)

tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching { exclude(coverageExcludes) })
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching { exclude(coverageExcludes) })
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
