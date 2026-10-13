// EmbeddingGemma 2 in process: the q8 ONNX model on ONNX Runtime, bundled in blog-mcp-gemma2.jar
// (ADR 0010). No model server and no network at runtime.
plugins {
    java
}

// The q8 text model at the pinned onnx-community revision, resolved and verified by Gradle. The
// graph names its weights file, so both keep their published names in the jar.
val huggingFaceAssets = configurations.create("huggingFaceAssets") { isTransitive = false }

dependencies {
    implementation(project(":core"))
    implementation(project(":providers:gemma2-common"))
    implementation(libs.onnxruntime)

    // Reads core's gemma2-image-reference.json in the image parity tests (ADR 0011).
    testImplementation(libs.jackson.databind)

    listOf("onnx", "onnx_data").forEach { fileExtension ->
        huggingFaceAssets("onnx-community:embeddinggemma-2-ONNX:daa72c51243991dfcaf9f9137d2c573d8f7790c0") {
            artifact {
                name = "model_quantized"
                classifier = "onnx"
                type = fileExtension
                extension = fileExtension
            }
        }
    }
}

tasks.processResources {
    from(huggingFaceAssets) {
        into("embeddinggemma-2-onnx")
        rename { name -> "model_quantized." + name.substringAfterLast('.') }
    }
}

// ADR 0011: the six image fixtures and their official-weights reference live in core; the image
// preprocessing and parity tests read them from this module's test classpath.
tasks.processTestResources {
    from(project(":core").layout.projectDirectory.dir("src/test/resources")) {
        include("gemma2-image-reference.json", "*.png", "*.jpg")
    }
}
