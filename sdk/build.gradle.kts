plugins {
    `java-library`
    jacoco
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    // 반환 타입이 model의 record라서 api로 노출한다. 그 외 런타임 의존성은 없다.
    api(project(":model"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// 레포의 data/v1이 곧 스냅샷이다. 빌드할 때 그대로 jar에 넣는다.
val snapshotDir = rootProject.layout.projectDirectory.dir("data/v1")

tasks.processResources {
    from(snapshotDir) {
        include("meta.tsv", "admin-dong.tsv", "legal-dong.tsv", "admin-legal-link.tsv", "succession.tsv")
        into("org/nowstart/kradmincodes/sdk/snapshot")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
    }
}

tasks.jacocoTestCoverageVerification {
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
