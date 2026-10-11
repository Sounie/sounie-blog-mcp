// EmbeddingGemma 2 served by LM Studio's OpenAI-compatible endpoint: blog-mcp-gemma2-server.jar
// (ADR 0010).
plugins {
    java
}

dependencies {
    implementation(project(":core"))
    implementation(project(":providers:gemma2-common"))
    implementation(libs.langchain4j.openai)
    // langchain4j-core brings Jackson 2 transitively; see providers/onnx.
    implementation(platform(libs.jackson2.bom))
}

// Opt-in: the adapter against the real model served by LM Studio on localhost:1234.
tasks.register<Test>("localServerTest") {
    group = "verification"
    description = "Runs the tests that need a local OpenAI-compatible model server (LM Studio)."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("local-server") }
}
