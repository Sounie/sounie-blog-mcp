# Gemma2 vision: what it depends on

This reference lists everything the image path of the in-process `gemma2` provider needs at build,
test and run time. The decisions behind it are in [ADR 0011](../adr/0011-image-embeddings-for-gemma2.md),
ADR 0010 (the bundled text model) and ADR 0008 (dependency verification). The delivery plan is
[image-embeddings.md](../plans/image-embeddings.md).

Line numbers are as of phase 1 (`feature/gemma2-image-embedding`, PR #22). Treat them as pointers.

## Summary

To embed an image, the `gemma2` provider needs four things:
- **ONNX Runtime Java 1.31.0**, the only library on the image path;
- **the q8 vision encoder**, `vision_encoder_quantized.onnx` and its weights `.onnx_data`;
- **the q8 text model** that ADR 0010 already bundles. The vision encoder only produces
  `image_features`; the text model turns them and the image tokens into the 768-value embedding;
- **core's SPI types**: `ImagePixels`, `ImageEmbedder` and `EmbeddingProvider.imageEmbedder()`.

Everything else is plain JDK code. Images use no tokenizer: their input ids are a fixed sequence
built by `ImageInputIds`.

Only `gemma2` can embed images. `onnx` (BGE) and `gemma2-server` (LM Studio) return
`Optional.empty()` from `imageEmbedder()`.

## For people running the jar

| Topic | What to expect |
|---|---|
| Disk | The vision encoder adds about 195 MB to the `gemma2` jar (ADR 0011, Consequences). The text model's weights are another 314 MB. |
| Temp space | On first use, each model is extracted into its own `blog-mcp-model-*` directory under `java.io.tmpdir`, and deleted on JVM exit (`BundledModelFiles`). The vision encoder needs about 195 MB there, and the text model about 314 MB. |
| Network | None at runtime. All model files are inside the jar. |
| Loading | Lazy. The text model loads on the first embedding of any kind. The vision encoder loads only on the first image, so a process that only embeds text never loads it (`Gemma2OnnxEmbedder`, holder classes `Model` and `Vision`). |
| Hardware | CPU, with ONNX Runtime's default session options. ONNX Runtime's native library ships inside its jar, for the platforms it supports. |
| Cost per image | One vision-encoder pass, then about 270 tokens through the text model. Every fixture used 2,520 patches, 266 image tokens and 270 input ids. |
| Input limits | `ImagePixels` accepts at most 16,777,216 pixels (4,096², 48 MiB of RGB samples). The image between the two resize passes is also capped at 48 MiB (`IntermediateSize`). Only very tall, narrow images (48 px wide after resizing, more than 349,525 px tall) hit that cap. They fail as `EmbedderUnavailable`. |
| Memory | No overall RAM figure has been measured. Known peak: for a very wide image at the pixel cap (16,777,216 × 1), the resize's weight table briefly takes about 268 MB on the Java heap. That fits the JVM's default heap (a quarter of RAM). |
| Failures | Any preprocessing or model failure surfaces as `EmbedderUnavailable`. Calls are serialised by one model lock. Preprocessing runs outside it. |

## Libraries

| Library | Version | Scope | Image path? | Declared in |
|---|---|---|---|---|
| `com.microsoft.onnxruntime:onnxruntime` | 1.31.0 | implementation | **Yes**: both graphs run on it. 1.31 or later is needed for the q8 graphs (1.20 rejects them). | `gradle/libs.versions.toml`, `providers/gemma2/build.gradle.kts` |
| `ai.djl.huggingface:tokenizers` | 0.38.0 | implementation, via `:providers:gemma2-common` | **No**. Text only (`Gemma2TokenCounter`). It's on the classpath because `Gemma2OnnxEmbedder` also embeds text, but its holder class isn't loaded for images. | `providers/gemma2-common/build.gradle.kts` |
| `tools.jackson.core:jackson-databind` | 3.2.3 | test | Tests only: reads the reference JSON (`ImageReference`). | `providers/gemma2/build.gradle.kts` |
| JUnit (BOM 6.1.3), AssertJ 3.27.7, ArchUnit 1.5.1 | as listed | test | Tests only. Added to every module. | root `build.gradle.kts` |

There are no other new libraries. Resizing, patching and the input ids are pure Java.

## Model artifacts

### What is pinned

All four files come from one pinned revision of `onnx-community/embeddinggemma-2-ONNX`. They are
declared in the `huggingFaceAssets` configuration of `providers/gemma2/build.gradle.kts`:

```
onnx-community:embeddinggemma-2-ONNX:daa72c51243991dfcaf9f9137d2c573d8f7790c0
  onnx/model_quantized.onnx             text model graph (q8)          ~0.5 MB
  onnx/model_quantized.onnx_data        text model weights             ~314 MB
  onnx/vision_encoder_quantized.onnx    vision encoder graph (q8)      ~0.2 MB
  onnx/vision_encoder_quantized.onnx_data  vision encoder weights      ~195 MB
```

- The text path also needs the tokenizer, `google:embeddinggemma-2:914f7f89…` `tokenizer.json`, from
  `providers/gemma2-common`. The image path doesn't.
- Variants not used:
  - fp32 vision encoder (671 MB): the fallback if q8 had missed parity. It didn't.
  - q4: worst-case cosine against fp32 is only 0.975.
  - fp16: the model card warns that it can produce NaN.

### How Gradle gets them

- The root `build.gradle.kts` declares an Ivy repository named `HuggingFace`, inside `exclusiveContent`.
  - It's limited to the groups `google` and `onnx-community`.
  - Its pattern is `[organisation]/[module]/resolve/[revision]/([classifier]/)[artifact].[ext]`.
- By default it reads from `https://huggingface.co/`.
- `-PhuggingFaceMirror=<dir>` points it at a local directory with the same layout. The Claude Code
  sandbox blocks huggingface.co, so builds inside it use `-PhuggingFaceMirror=.hf-mirror`.
  - The mirror isn't committed (`.gitignore`).
  - It must contain `onnx-community/embeddinggemma-2-ONNX/resolve/daa72c51…/onnx/` with the four
    files above.

### How they get into the jar

- `processResources` in `providers/gemma2/build.gradle.kts` copies the files into `embeddinggemma-2-onnx/`.
- Each file is renamed to `<graph>.<extension>`. Gradle names the file `<graph>-<revision>-onnx.<ext>`
  from huggingface.co, but `<graph>.<ext>` from the mirror.
- The names must stay as published: each graph refers to its weights file by name, and ONNX Runtime
  loads the weights from a file beside the graph.
- The shadow jar uses Zip64, because of its size.

### Checksums (ADR 0008)

`gradle/verification-metadata.xml` verifies every file under the component
`onnx-community:embeddinggemma-2-ONNX:daa72c51…`, with artifacts named
`<graph>-daa72c51…-onnx.<ext>`.

To change the revision or add a graph:
1. Edit the pin in `providers/gemma2/build.gradle.kts`.
2. Run `scripts/update-verification-metadata` outside the sandbox, so the files come from
   huggingface.co rather than a local mirror. It should only add entries.
3. If a checksum changes for a version that's already pinned, stop: that's a supply-chain alarm.
4. Never edit checksums by hand.

## ONNX graph interface

| Graph | Inputs | Output |
|---|---|---|
| Vision encoder (`Gemma2VisionEncoder`) | `pixel_values` float `[1, 2520, 768]`; `pixel_position_ids` int64 `[1, 2520, 2]`. Loading fails unless these are exactly the declared inputs. | `image_features` float `[n, 512]`, where n is the image token count. A different row count fails. |
| Text model (`Gemma2OnnxModel`) | `input_ids` and `attention_mask` int64 `[1, L]`; `image_features`, `video_features`, `audio_features` float `[rows, 512]` | `sentence_embedding` float `[1, 768]`, already mean-pooled and L2-normalised |

- For an image, the input ids are `[2, 255999, 258880 × n, 258882, 1]`. The text graph replaces
  each `258880` soft token with one row of `image_features`.
- Text calls pass zero-row media tensors. Video and audio are always empty.

## Pure-Java preprocessing

These classes are in `providers/gemma2/.../gemma2/onnx/`. They are a port of transformers'
`image_processing_pil_gemma4.py`, and use only the JDK and core's `ImagePixels`:

| Class | Job |
|---|---|
| `TargetSize`, `PatchGrid` | Fit 280 × 9 patches of 16 px, floor each side to a multiple of 48, with the fallback for a side that floors to zero |
| `IntermediateSize`, `ImageTooLargeToResize` | Refuse a resize intermediate over 48 MiB, before allocating it |
| `PilBicubicResize`, `BicubicAxis`, `ResizedImage` | PIL's antialiased bicubic: two passes, fixed point, ported from `Resample.c` |
| `Patchifier`, `VisionInput` | Rescale by 1/255, cut patches (row, column, channel), (x, y) positions, pad to 2,520 with 0 and −1 |
| `ImageInputIds` | The fixed input-id sequence |
| `Gemma2ImagePreprocessor` | `TargetSize.of`, then resize, then patchify |

- **Why port PIL:** the JDK's `AffineTransformOp` is a different filter (ADR 0011).
- **Parity:** the Java resize matches Pillow byte for byte. The reference JSON came from
  torchvision's resize, which differs by up to 2 levels in about 0.5% of samples. That's within the
  tests' tolerance (ADR 0011, Parity).
- **Decoding:** reading image files into `ImagePixels` (`javax.imageio`) is only in tests today
  (`FixtureImages`). Phase 3 (`HttpImageSource`) and phase 4 (local files) add it to production
  code. JPEG, PNG and GIF are supported; WebP is skipped.

## Core SPI types

They are in `core/src/main/java/nz/sounie/blogmcp/search/`:

| Type | Package | Role |
|---|---|---|
| `EmbeddingProvider.imageEmbedder()` | `application.embedding.spi` | `Optional<ImageEmbedder>`. Abstract, so every provider must answer. Asking for it loads no model. |
| `ImageEmbedder` | `domain.embedding` | Port: `Embedding embedImage(ImagePixels)` |
| `ImagePixels`, `InvalidImagePixels` | `domain.embedding` | Validated, immutable RGB raster |
| `Embedding`, `EmbeddingFactory` | `domain.embedding` | The result (`createGemma2Embedding`) |
| `EmbedderUnavailable` | `domain.embedding` | Every image failure |

## Test and reference dependencies

- **Reference data:** `core/src/test/resources/gemma2-image-reference.json` and the six fixtures
  beside it:
  - `black-swan-in-reeds.png`, `grapes-on-trellis.png`, `image-management-services.jpg`,
    `quiz-slide-sloth.png`, `race-car-at-circuit.png` and `rugby-stadium-smoke.png`.
  - gemma2's `processTestResources` copies them onto its test classpath.
- **Model tests run in plain `test`:**
  - `@Tag("model")` tests are not excluded from the `test` task (it excludes only `jar` and
    `local-server`). So `./gw :providers:gemma2:test` and `./gw check` resolve and load the real
    model files.
  - Inside the sandbox, add `-PhuggingFaceMirror=.hf-mirror`.
  - pitest excludes `model`.
- **Thresholds** (`Gemma2OnnxImageEmbeddingTest`): image cosine against the official model ≥ 0.994,
  and query cosine ≥ 0.999. Measured: 0.99908 to 0.99980.
- **Regenerating the reference** (`scripts/gemma2-image-reference.py`). This is only needed if the
  pins or fixtures change.
  - Python 3.12 in `.venv-spike`, with `transformers>=5.19`, `torch`, `torchvision`,
    `onnxruntime`, `pillow` and `numpy`. The commands are in the script's docstring.
  - It runs Google's official fp32 weights (`google/embeddinggemma-2@914f7f89…`), so it must run
    outside the sandbox.
  - None of this is needed at build or run time.

## What isn't a dependency

- **No Python, PyTorch or transformers at runtime.** They're only for regenerating the reference.
- **No DJL for images.** The tokenizer is text only.
- **No model server.** LM Studio's `/v1/embeddings`, Ollama and llama.cpp's GGUF build document no
  image input for this model (ADR 0011, Context). That's why `gemma2-server` stays text-only.
- **No hosted API.** None exists for EmbeddingGemma 2.
