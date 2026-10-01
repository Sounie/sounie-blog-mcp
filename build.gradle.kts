plugins {
    java
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.pitest)
}

group = "nz.sounie"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jsoup)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.wiremock.standalone)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Coverage is gated on the domain and application layers only; adapters are covered by
// integration tests where they add value, not by a global percentage.
val gatedLayers = listOf("**/domain/**", "**/application/**")

tasks.jacocoTestReport {
    reports { xml.required = true; html.required = true }
}

tasks.jacocoTestCoverageVerification {
    classDirectories.setFrom(
        files(classDirectories.files.map { fileTree(it) { include(gatedLayers) } })
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }

spotless {
    java {
        googleJavaFormat(libs.versions.googleJavaFormat.get())
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// Mutation testing focuses on the domain, where the business rules live.
// Versions are read outside the block: inside it, `pitest` resolves to the plugin extension.
val pitestCoreVersion = libs.versions.pitestCore.get()
val pitestJunit5Version = libs.versions.pitestJunit5.get()

pitest {
    pitestVersion = pitestCoreVersion
    junit5PluginVersion = pitestJunit5Version
    targetClasses = setOf("nz.sounie.blogmcp.*.domain.*")
    targetTests = setOf("nz.sounie.blogmcp.*")
    mutationThreshold = 80
    timestampedReports = false
    outputFormats = setOf("HTML", "XML")
    failWhenNoMutations = false
}
