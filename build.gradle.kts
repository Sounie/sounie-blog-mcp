import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import java.net.URI

plugins {
    base
    alias(libs.plugins.spotless)
    alias(libs.plugins.pitest) apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.spotbugs) apply false
}

// The root project only aggregates; it formats its own build scripts.
spotless {
    kotlinGradle {
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// ADR 0010: pinned model files come from Hugging Face. `-PhuggingFaceMirror=<dir>` reads the same
// layout (<org>/<repo>/resolve/<revision>/...) from a local directory instead, for builds that
// cannot reach huggingface.co, such as the Claude Code sandbox.
val huggingFace: URI =
    providers.gradleProperty("huggingFaceMirror").map { file(it).toURI() }
        .getOrElse(URI("https://huggingface.co/"))

// The root's version catalog; inside `configure {}`, `libs` would resolve against the subproject.
val catalog = libs

val javaProjects =
    listOf(
        ":core",
        ":providers:onnx",
        ":providers:gemma2-common",
        ":providers:gemma2",
        ":providers:gemma2-server",
    ).map(::project)

configure(javaProjects) {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "com.github.spotbugs")

    group = "nz.sounie"
    version = "0.1.0"

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion = JavaLanguageVersion.of(25) }
    }

    repositories {
        mavenCentral()
        exclusiveContent {
            forRepository {
                ivy {
                    name = "HuggingFace"
                    url = huggingFace
                    patternLayout {
                        artifact("[organisation]/[module]/resolve/[revision]/([classifier]/)[artifact].[ext]")
                    }
                    metadataSources { artifact() }
                }
            }
            filter {
                includeGroup("google")
                includeGroup("onnx-community")
            }
        }
    }

    dependencies {
        "testImplementation"(platform(catalog.junit.bom))
        "testImplementation"(catalog.junit.jupiter)
        "testImplementation"(catalog.assertj.core)
        "testImplementation"(catalog.archunit.junit5)
        "testRuntimeOnly"(catalog.junit.platform.launcher)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    }

    tasks.withType<Test>().configureEach {
        // ADR 0005: DJL extracts its native tokenizer library to ~/.djl.ai by default, which the
        // sandbox cannot write. DJL 0.36.0 reads DJL_CACHE_DIR as an environment variable or system
        // property.
        systemProperty(
            "DJL_CACHE_DIR",
            rootProject.layout.buildDirectory.dir("djl-cache").get().asFile.absolutePath,
        )
        // DJL loads its tokenizer library through JNA, which calls a restricted JDK method.
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }

    tasks.named<Test>("test") {
        // AC-APP-12's jar tests need a distribution jar; they run in each provider's `jarTest`.
        // Tests that need a locally running model server run in `localServerTest`.
        useJUnitPlatform { excludeTags("jar", "local-server") }
    }

    // Complexity budget (ADR 0004): keeps execution paths per method small, so each type needs few
    // tests. Only the complexity rules run, and only on production code.
    // PMD runs through its CLI in a plain JavaExec rather than Gradle's `pmd` plugin: the plugin's
    // worker daemon must connect back to Gradle over localhost, which the Claude Code sandbox blocks.
    val pmdCli = configurations.create("pmdCli")
    dependencies {
        pmdCli(catalog.pmd.cli) { exclude(group = "net.sourceforge.pmd", module = "pmd-designer") }
        pmdCli(catalog.pmd.java)
    }
    val pmdComplexity =
        tasks.register<JavaExec>("pmdComplexity") {
            group = "verification"
            description = "Fails if any production method exceeds the complexity budget (ADR 0004)."
            val sources = layout.projectDirectory.dir("src/main/java")
            val ruleset = rootProject.layout.projectDirectory.file("config/pmd/complexity.xml")
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
    tasks.named("check") { dependsOn(pmdComplexity) }

    // Security scan (ADR 0008): SpotBugs with FindSecBugs, security findings only, production code
    // only. `check` depends on spotbugsMain through the plugin; any finding fails the build.
    dependencies { "spotbugsPlugins"(catalog.findsecbugs) }
    extensions.configure<SpotBugsExtension> {
        toolVersion = catalog.versions.spotbugs.get()
        ignoreFailures = false
        includeFilter = rootProject.file("config/spotbugs/security-include.xml")
    }
    tasks.named<SpotBugsTask>("spotbugsMain") {
        reports.create("html") {
            required = true
            outputLocation = layout.buildDirectory.file("reports/spotbugs/main.html")
        }
    }
    tasks.named("spotbugsTest") { enabled = false }

    extensions.configure<SpotlessExtension> {
        java {
            googleJavaFormat(catalog.versions.googleJavaFormat.get())
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
}

/** What a provider module's distribution jar must contain (AC-SRCH-40, AC-SRCH-42). */
class Distribution(
    val jarName: String,
    val provider: String,
    val providerPackages: List<String>,
    val profileKey: String,
    /** Jar tests left out because they need something CI lacks, such as a model server. */
    val excludedJarTags: List<String> = emptyList(),
)

val embedding = "nz.sounie.blogmcp.search.adapter.out.embedding"
val distributions =
    mapOf(
        ":providers:onnx" to
            Distribution(
                jarName = "blog-mcp-onnx",
                provider = "$embedding.onnx.OnnxEmbedder",
                providerPackages = listOf("$embedding.onnx"),
                profileKey = "onnx-bge-small-en-v1.5-q-v1",
            ),
        ":providers:gemma2" to
            Distribution(
                jarName = "blog-mcp-gemma2",
                provider = "$embedding.gemma2.onnx.Gemma2OnnxEmbedder",
                providerPackages = listOf("$embedding.gemma2", "$embedding.gemma2.onnx"),
                profileKey = "gemma2-onnx-q8-daa72c51-v1",
            ),
        ":providers:gemma2-server" to
            Distribution(
                jarName = "blog-mcp-gemma2-server",
                provider = "$embedding.gemma2.server.Gemma2Embedder",
                providerPackages = listOf("$embedding.gemma2", "$embedding.gemma2.server"),
                profileKey = "gemma2-914f7f89-v1",
                // Searching through the jar needs LM Studio; `localServerTest` covers the adapter.
                excludedJarTags = listOf("model"),
            ),
    )

distributions.forEach { (path, distribution) ->
    project(path) {
        apply(plugin = "com.gradleup.shadow")

        // One runnable jar: core, this provider and its dependencies.
        val shadowJar =
            tasks.named<ShadowJar>("shadowJar") {
                archiveBaseName = distribution.jarName
                archiveClassifier = ""
                archiveVersion = ""
                isZip64 = true
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

        // Runs the server with this provider, e.g. from the IDE.
        tasks.register<JavaExec>("run") {
            group = "application"
            description = "Runs the blog-mcp server with this module's embedding provider."
            classpath = project.the<SourceSetContainer>()["main"].runtimeClasspath
            mainClass = "nz.sounie.blogmcp.app.Main"
            jvmArgs("--enable-native-access=ALL-UNNAMED")
            standardInput = System.`in`
        }

        // AC-APP-12 and AC-SRCH-40/42: core's jar tests, plus this module's own, against its jar.
        tasks.register<Test>("jarTest") {
            group = "verification"
            description = "Runs the distribution jar as an MCP server under the real MCP client."
            val coreTest = project(":core").the<SourceSetContainer>()["test"]
            val ownTest = project.the<SourceSetContainer>()["test"]
            testClassesDirs = coreTest.output.classesDirs + ownTest.output.classesDirs
            classpath = coreTest.runtimeClasspath + ownTest.runtimeClasspath
            useJUnitPlatform {
                includeTags("jar")
                excludeTags(*distribution.excludedJarTags.toTypedArray())
            }
            val jar = shadowJar.flatMap { it.archiveFile }
            inputs.file(jar)
            systemProperty("blogmcp.expectedProvider", distribution.provider)
            systemProperty("blogmcp.providerPackages", distribution.providerPackages.joinToString(","))
            systemProperty("blogmcp.expectedProfileKey", distribution.profileKey)
            doFirst { systemProperty("blogmcp.distributionJar", jar.get().asFile.absolutePath) }
        }
    }
}
