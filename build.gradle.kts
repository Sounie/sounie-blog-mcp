import java.net.URI
import java.security.MessageDigest
import java.util.HexFormat

plugins {
    java
    jacoco
    alias(libs.plugins.spotless)
    alias(libs.plugins.pitest)
    alias(libs.plugins.shadow)
    alias(libs.plugins.spotbugs)
}

group = "nz.sounie"
version = "0.1.0"

val embeddingProvider =
    providers.gradleProperty("embeddingProvider").orNull?.trim()?.also { selection ->
        require(selection == "onnx" || selection == "gemma2") {
            "embeddingProvider must be exactly 'onnx' or 'gemma2', but was '$selection'"
        }
    } ?: error("Required Gradle property embeddingProvider must be 'onnx' or 'gemma2'")

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

dependencies {
    implementation(libs.jackson.databind)
    implementation(libs.jsoup)
    compileOnly(libs.langchain4j.embeddings.bge.small.en.v15.q)
    compileOnly(libs.langchain4j.openai)
    compileOnly("ai.djl.huggingface:tokenizers:0.36.0")
    if (embeddingProvider == "onnx") {
        runtimeOnly(libs.langchain4j.embeddings.bge.small.en.v15.q)
    } else {
        runtimeOnly(libs.langchain4j.openai)
        runtimeOnly("ai.djl.huggingface:tokenizers:0.36.0")
    }

    // langchain4j-core brings Jackson 2 transitively. Declaring its BOM lifts it to a patched version
    // and gives Dependabot a declared version to update for future advisories.
    implementation(platform(libs.jackson2.bom))
    implementation(libs.mcp.core)
    implementation(libs.mcp.json.jackson3)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.wiremock.standalone)
    testImplementation(libs.langchain4j.embeddings.bge.small.en.v15.q)
    testImplementation(libs.langchain4j.openai)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val generatedProviderResources = layout.buildDirectory.dir("generated/embedding-provider-resources")
val generateProviderResources =
    tasks.register("generateProviderResources") {
    outputs.dir(generatedProviderResources)
    inputs.property("embeddingProvider", embeddingProvider)
    inputs.files(
        "src/provider/gemma2/LICENSE-EmbeddingGemma-2.0.txt",
        "src/provider/gemma2/NOTICE-EmbeddingGemma.txt",
    )
    doLast {
        val output = generatedProviderResources.get().asFile
        output.deleteRecursively()
        val serviceFile =
            output.resolve("META-INF/services/nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider")
        serviceFile.parentFile.mkdirs()
        serviceFile.writeText(
            if (embeddingProvider == "onnx") {
                "nz.sounie.blogmcp.search.adapter.out.OnnxEmbedder\n"
            } else {
                "nz.sounie.blogmcp.search.adapter.out.Gemma2Embedder\n"
            },
        )
        if (embeddingProvider == "gemma2") {
            val tokenizer =
                URI(
                        "https://huggingface.co/google/embeddinggemma-2/resolve/914f7f89142e33e77833254d9c9b90c3cef7303b/tokenizer.json",
                    )
                    .toURL()
                    .openStream()
                    .use { it.readAllBytes() }
            val actualHash =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(tokenizer))
            check(
                actualHash == "4d777ef5bdc1aa36227abdfb77c3e49e7b9c892d16e1b6bda41c393504828be4",
            ) {
                "Pinned Gemma2 tokenizer checksum mismatch: $actualHash"
            }
            output.resolve("embeddinggemma-2/tokenizer.json").apply {
                parentFile.mkdirs()
                writeBytes(tokenizer)
            }
            val license = file("src/provider/gemma2/LICENSE-EmbeddingGemma-2.0.txt").readBytes()
            val licenseText = String(license, Charsets.UTF_8)
            check(licenseText.contains("Apache License") && licenseText.contains("Version 2.0")) {
                "The pinned EmbeddingGemma license is not Apache 2.0"
            }
            output.resolve("META-INF/licenses/LICENSE-EmbeddingGemma-2.0.txt").apply {
                parentFile.mkdirs()
                writeBytes(license)
            }
            output.resolve("META-INF/NOTICE-EmbeddingGemma.txt").apply {
                parentFile.mkdirs()
                writeBytes(file("src/provider/gemma2/NOTICE-EmbeddingGemma.txt").readBytes())
            }
        }
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

// The server-process tests (app.md section 6) start Main in a subprocess on this classpath.
val mainRuntimeClasspath: FileCollection = sourceSets.main.get().runtimeClasspath

tasks.test {
    // AC-APP-12's jar tests need the shadow jar; they run in `jarTest` instead.
    useJUnitPlatform { excludeTags("jar") }
    finalizedBy(tasks.jacocoTestReport)
    doFirst { systemProperty("blogmcp.runtimeClasspath", mainRuntimeClasspath.asPath) }
    // ADR 0005: DJL extracts its native tokenizer library to ~/.djl.ai by default, which the sandbox
    // cannot write. DJL 0.36.0 reads DJL_CACHE_DIR as an environment variable or system property.
    val djlCache = layout.buildDirectory.dir("djl-cache")
    systemProperty("DJL_CACHE_DIR", djlCache.get().asFile.absolutePath)
    // DJL loads its tokenizer library through JNA, which calls a restricted JDK method.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.processResources {
    dependsOn(generateProviderResources)
    from(generatedProviderResources) { exclude("META-INF/services/**") }
}

// One runnable jar, named for its selected provider.
tasks.shadowJar {
    archiveBaseName = "blog-mcp-$embeddingProvider"
    archiveClassifier = ""
    archiveVersion = ""
    dependsOn(generateProviderResources)
    from(generatedProviderResources) { include("META-INF/services/**") }
    if (embeddingProvider == "onnx") {
        exclude(
            "nz/sounie/blogmcp/search/adapter/out/Gemma2Embedder*.class",
            "nz/sounie/blogmcp/search/adapter/out/Gemma2TokenCounter.class",
        )
    } else {
        exclude(
            "nz/sounie/blogmcp/search/adapter/out/OnnxEmbedder*.class",
            "nz/sounie/blogmcp/search/adapter/out/BgeTokenCounter.class",
        )
    }
    // Required: McpJsonDefaults finds mcp-json-jackson3 through ServiceLoader.
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    // A signed dependency's signatures would not match the merged jar.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    manifest {
        attributes(
            "Main-Class" to "nz.sounie.blogmcp.app.Main",
            "Implementation-Version" to project.version.toString(),
            // JEP 472: DJL (through JNA) and ONNX Runtime call restricted methods.
            "Enable-Native-Access" to "ALL-UNNAMED",
        )
    }
}

// AC-APP-12: the shadow jar runs as an MCP server under the real client. Separate from `test`
// because it needs the ~130 MB jar and the real model.
val jarTest by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs the shadow jar as an MCP server under the real MCP client (AC-APP-12)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("jar") }
    val shadowJarFile = tasks.shadowJar.flatMap { it.archiveFile }
    inputs.file(shadowJarFile)
    systemProperty(
        "DJL_CACHE_DIR",
        layout.buildDirectory.dir("djl-cache").get().asFile.absolutePath,
    )
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    doFirst {
        val distributionJar = shadowJarFile.get().asFile.absolutePath
        systemProperty("blogmcp.shadowJar", distributionJar)
        systemProperty("blogmcp.distributionJar", distributionJar)
        systemProperty(
            "blogmcp.distributionLauncher",
            layout.projectDirectory.file("${System.getProperty("java.home")}/bin/java").asFile.absolutePath,
        )
        systemProperty("blogmcp.embeddingProvider", embeddingProvider)
    }
}

// Complexity budget (ADR 0004): keeps execution paths per method small, so each type needs few
// tests. Only the complexity rules run, and only on production code.
// PMD runs through its CLI in a plain JavaExec rather than Gradle's `pmd` plugin: the plugin's
// worker daemon must connect back to Gradle over localhost, which the Claude Code sandbox blocks.
val pmdCli: Configuration by configurations.creating

dependencies {
    pmdCli(libs.pmd.cli) { exclude(group = "net.sourceforge.pmd", module = "pmd-designer") }
    pmdCli(libs.pmd.java)
}

val pmdComplexity by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Fails if any production method exceeds the complexity budget (ADR 0004)."
    val sources = layout.projectDirectory.dir("src/main/java")
    val ruleset = layout.projectDirectory.file("config/pmd/complexity.xml")
    inputs.dir(sources)
    inputs.file(ruleset)
    classpath = pmdCli
    mainClass = "net.sourceforge.pmd.cli.PmdCli"
    args(
        "check",
        "--dir", sources.asFile.path,
        "--rulesets", ruleset.asFile.path,
        "--format", "text",
        "--no-cache",
        "--no-progress",
    )
}

tasks.check { dependsOn(pmdComplexity) }

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

// Security scan (ADR 0008): SpotBugs with FindSecBugs, security findings only, production code only.
// `check` depends on spotbugsMain through the plugin; any finding fails the build.
dependencies { spotbugsPlugins(libs.findsecbugs) }

spotbugs {
    toolVersion = libs.versions.spotbugs.get()
    ignoreFailures = false
    includeFilter = file("config/spotbugs/security-include.xml")
}

tasks.spotbugsMain {
    reports.create("html") {
        required = true
        outputLocation = layout.buildDirectory.file("reports/spotbugs/main.html")
    }
}

tasks.spotbugsTest { enabled = false }

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
    // The real-model adapter tests (@Tag("model")) exercise adapter.out, not the domain under
    // mutation; excluding them keeps PIT independent of the 34 MB model and the native caches.
    excludedGroups = setOf("model")
}
