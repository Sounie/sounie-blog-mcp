// ONNX BGE-small-en-v1.5 (quantised) through LangChain4j: blog-mcp-onnx.jar (ADR 0010).
plugins {
    java
}

dependencies {
    implementation(project(":core"))
    implementation(libs.langchain4j.embeddings.bge.small.en.v15.q)
    implementation(libs.djl.tokenizers)
    // langchain4j-core brings Jackson 2 transitively. Declaring its BOM lifts it to a patched version
    // and gives Dependabot a declared version to update for future advisories.
    implementation(platform(libs.jackson2.bom))
}
