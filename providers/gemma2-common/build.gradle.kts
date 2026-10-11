// What both EmbeddingGemma 2 providers share: prompts, the pinned tokenizer and its licence
// (ADR 0010). A library; it has no distribution jar of its own.
plugins {
    java
}

// The tokenizer at the pinned google/embeddinggemma-2 revision, resolved and verified by Gradle.
val huggingFaceAssets = configurations.create("huggingFaceAssets") { isTransitive = false }

dependencies {
    implementation(project(":core"))
    implementation(libs.djl.tokenizers)

    huggingFaceAssets("google:embeddinggemma-2:914f7f89142e33e77833254d9c9b90c3cef7303b") {
        artifact {
            name = "tokenizer"
            type = "json"
            extension = "json"
        }
    }
}

tasks.processResources {
    from(huggingFaceAssets) {
        into("embeddinggemma-2")
        rename { "tokenizer.json" }
    }
}
