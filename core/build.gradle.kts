// The app: domain, application, non-model adapters and the composition root (ADR 0010). It depends
// on no embedding-model library; each provider module adds one.
plugins {
    java
    jacoco
    id("info.solidsoft.pitest")
}

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jsoup)
    implementation(libs.mcp.core)
    implementation(libs.mcp.json.jackson3)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(libs.wiremock.standalone)
}

// The server-process tests (app.md section 6) start Main in a subprocess on this classpath.
val mainRuntimeClasspath: FileCollection = sourceSets.main.get().runtimeClasspath

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
    doFirst { systemProperty("blogmcp.runtimeClasspath", mainRuntimeClasspath.asPath) }
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

// Mutation testing focuses on the domain, where the business rules live.
// Versions are read outside the block: inside it, `pitest` resolves to the plugin extension.
val pitestCoreVersion = libs.versions.pitestCore.get()
val pitestJunit5Version = libs.versions.pitestJunit5.get()

pitest {
    pitestVersion = pitestCoreVersion
    junit5PluginVersion = pitestJunit5Version
    targetClasses = setOf("nz.sounie.blogmcp.*.domain.*")
    // Domain mutants are killed by domain tests: each decision is tested where it's made (CLAUDE.md).
    // Running only those keeps the subprocess, WireMock and file-store tests out of PIT's coverage pass.
    targetTests = setOf("nz.sounie.blogmcp.*.domain.*")
    threads = Runtime.getRuntime().availableProcessors()
    // A few mutants (ChunkingPolicy, WordSequence) loop while allocating. Without a cap, each minion
    // defaults to a quarter of RAM, and several at once can exhaust a CI runner.
    jvmArgs = listOf("-Xmx256m")
    mutationThreshold = 80
    timestampedReports = false
    outputFormats = setOf("HTML", "XML")
    failWhenNoMutations = false
    excludedGroups = setOf("model")
}
