# 10. Provider modules and in-process EmbeddingGemma 2

Date: 2026-10-11

## Status

Accepted by the owner on 2026-10-11 and implemented on `feature/provider-distribution`; see
[Implementation status](#implementation-status). It replaces decisions 1, 3, 9 and 11 of ADR 0009,
and the index paths in its decision 7 (see decision 8), and defers decision 10 (see decision 7).
ADR 0009's other decisions are unchanged: the SPI placement, one provider instance serving both
ports, provider-keyed index subtrees, and the Gemma2 prompts and budget.

## Context

ADR 0009 chose the provider with a required Gradle property, `-PembeddingProvider`, in a single
module. Both provider libraries were `compileOnly`, and the jar dropped the other provider's classes
by filename pattern. Review of `feature/provider-distribution` found this fragile:
- Every CI and script invocation had to pass the property.
- Running `Main` from the IDE found no provider.
- The tokenizer was downloaded again on every provider switch, without caching or `--offline` support.
- The distribution tests ran on the build classpath, so a broken jar still passed.
- Nothing stopped one provider's code from importing the other's library.

The Gemma2 provider also needs a separate LM Studio server. The owner wants a Gemma2 provider that
runs in process, bundled with the app like ONNX BGE. The LM Studio provider stays as a third option.
The app must run on Linux and Windows as well as macOS.

**Research findings (2026-10-11):**
- `google/embeddinggemma-2` at `914f7f89…` is Apache-2.0 and not gated.
- In text-only use it has about 270M parameters: a 512-wide backbone, mean pooling, a learned
  512→768 projection and normalisation, with an 8,192-token context.
- `onnx-community/embeddinggemma-2-ONNX` at `daa72c51…` exports the text model with a
  `sentence_embedding` output. Its weights are an external `.onnx_data` file: q8 is 313.7 MB, and
  the worst-case cosine against fp32 is 0.9997. fp16 must not be used.
- LangChain4j's `OnnxEmbeddingModel` can't run this model correctly. It reads output 0 as
  `[1, seq, hidden]`, pools by itself, and splits text into 510-token chunks with WordPiece rules.
- No maintained Java llama.cpp binding supports the architecture yet.

**Natives checked in the jars we resolve:**

| Jar | Native libraries |
|---|---|
| ONNX Runtime 1.20.0 | linux-x64, linux-aarch64, osx-aarch64, osx-x64, win-x64 |
| DJL tokenizers 0.36.0 | linux-x86_64, linux-aarch64, osx-aarch64, win-x86_64 |

## Decision

1. **One Gradle module per provider.** `core/` holds the domain, the application layer (including the
   `search.application.embedding.spi` contract), every non-model adapter and the `app` composition
   root.
   - `core` depends on no embedding-model library, so the compiler enforces library isolation.
   - The provider modules each depend on `core` and on their own library only:
     - `providers/onnx` (LangChain4j BGE)
     - `providers/gemma2` (ONNX Runtime and the DJL tokenizer)
     - `providers/gemma2-server` (LangChain4j OpenAI)
   - `providers/gemma2-common` holds the Gemma2 prompts, the budget, the tokenizer and the licence
     files that both Gemma2 providers share.
2. **One distribution per provider module, with no build property.** Each provider module has a static
   `META-INF/services` entry and its own runnable jar:
   - `blog-mcp-onnx.jar`
   - `blog-mcp-gemma2.jar`
   - `blog-mcp-gemma2-server.jar`

   `./gw check` builds and tests every provider. ServiceLoader discovery and the startup failure on
   zero or several providers are unchanged.
3. **The Gemma2 model runs in process on ONNX Runtime Java.** It uses the q8 text model from
   `onnx-community/embeddinggemma-2-ONNX` at `daa72c51…` and the DJL tokenizer from `914f7f89…`.
   - The adapter feeds `input_ids` and `attention_mask` itself, with batch size 1.
   - It feeds empty `image_features`, `video_features` and `audio_features` tensors.
   - It reads `sentence_embedding` by name.
   - If the graph declares any other input, loading fails as `EmbedderUnavailable`.
   - The module pins ONNX Runtime 1.31.0 or later.
   - It makes no network calls at runtime.
   - The weights travel inside the jar. ONNX external data must sit beside the graph as a file, so
     on first use each process copies both files into its own new temporary directory. The
     directory is readable only by its user and deleted when the JVM exits. Nothing is shared or
     reused between processes, so there is no checksum-and-reuse logic, and no other process can
     replace the files. The SPI is unchanged: providers still get no data directory. The cost is
     about a second of copying on the first embed.
4. **Pinned model files are Gradle dependencies.** They are resolved through an Ivy repository on
   huggingface.co that is pinned to revisions and limited to those files. Gradle caches them, they
   work `--offline`, and their checksums are verified by `verification-metadata.xml` (ADR 0008). This
   replaces the download task written by hand.
5. **Each profile has its own key.**
   - The in-process provider uses `gemma2-onnx-q8-daa72c51-v1`.
   - The LM Studio provider keeps `gemma2-914f7f89-v1`, so its indexes stay valid.

   Each recipe names the model revision and its quantisation.
6. **Supported platforms:**

   | Platform | Status |
   |---|---|
   | Linux x64 | Tested in CI (the owner chose Linux-only CI) |
   | macOS arm64 | The development platform; tested locally |
   | Windows x64, Linux arm64 | Supported by the natives above, not tested |

7. **No data-directory lock for now.** This replaces ADR 0009 decision 10.
   - The server is stdio-based, with one per MCP client.
   - The owner plans to move it to an HTTP service later, and concurrency will be designed then.
   - Until then, different profiles are isolated by their subtrees, and catalog files are written
     atomically.
8. **Profile subtrees live under `search/indexes/`.** This replaces the paths in ADR 0009
   decision 7.
   - Binaries from before ADR 0009 scan `search/index/` at any depth, and would quarantine
     profile-keyed files found there as misplaced (review #4).
   - Legacy adoption still moves the old `search/index/<site>/` ONNX files, now into
     `search/indexes/onnx-bge-small-en-v1.5-q-v1/`.
   - An older binary run afterwards finds only its emptied legacy folder and rebuilds there, without
     touching the profile subtrees.

## Spike results (2026-10-11, macOS arm64, Java 25)

The spike was a throwaway program run against the pinned files. Each file's SHA-256 matches the Hub's
LFS id:

| File | SHA-256 |
|---|---|
| `onnx/model_quantized.onnx` | `d06edd60…` |
| `onnx/model_quantized.onnx_data` | `278a7ff1…` |
| `tokenizer.json` | `4d777ef5…` |

`tokenizer.json` is byte-identical to the one in `google/embeddinggemma-2` at `914f7f89…`.

- **ONNX Runtime must be 1.31.0 or later.** 1.20.0, the version LangChain4j's BGE module brings, rejects
  the graph: `Unrecognized attribute: bits for operator GatherBlockQuantized`. The gemma2 module pins
  its own ONNX Runtime. Its jar has native libraries for linux-x64, linux-aarch64, osx-aarch64 and
  win-x64.
- **Inputs:**
  - `input_ids` and `attention_mask`, both int64 `[batch, seq]`;
  - `image_features`, `video_features` and `audio_features`, float `[n, 512]`. These are all required.
    Text-only calls pass empty `[0, 512]` tensors, built from a zero-length buffer because Java arrays
    cannot have a zero dimension.
- **Outputs:** `last_hidden_state` `[batch, seq, 768]` and `sentence_embedding` `[batch, 768]`. The
  adapter reads `sentence_embedding` by name. The vectors are already L2-normalised (norm 1.0000).
- **Tokenizer:** DJL wraps the text in `<bos>` (2) and `<eos>` (1), as the model's tokenizer config
  declares. The parity test against LM Studio is the remaining check that the token IDs match what
  the model expects.
- **Quality check:** for the query "how do I configure a Gradle build for Java", the related passage
  scores cosine 0.765 and the unrelated one 0.531.
- **Performance:**
  - The session loads in about 70 ms.
  - Embedding a 2,999-token passage takes 1.15 s, and a short query 31 ms, after one warm-up call.
  - The JVM heap stays around 200 MB; the weights live in native memory.
- **Telemetry:** ONNX Runtime logs a telemetry warning when the environment is created, even after
  `setTelemetry(false)`. The adapter turns telemetry off anyway. The warning goes to stderr, which
  the MCP protocol does not use.

## Rejected alternatives

- **Keep the build property with source sets in one module.** It isolates compilation, but keeps the
  property and the hand-wired quality gates.
- **LangChain4j `OnnxEmbeddingModel`.** It has the wrong output handling, pools by itself and chunks
  at 510 tokens.
- **llama.cpp through `de.kherud:llama`.** Its bundled llama.cpp predates the model's architecture,
  and it has had no release since June 2025.
- **fp16 weights.** They risk NaN embeddings. **q4 weights** cost noticeable quality (a worst-case
  cosine of 0.975) to save about 140 MB.
- **Downloading the model on first run.** That needs network access at runtime, and the owner asked
  for it to be bundled.

## Consequences

- **AC changes:** AC-SRCH-41 (rejecting a missing property) is retired, and AC-SRCH-40 and 43 are
  restated per module. `CLAUDE.md` commands need no property.
- **Size:** `blog-mcp-gemma2.jar` is about 370 MB (371 MB as built). A cold CI cache downloads
  about 350 MB of model files once; setup-gradle caches them afterwards.
- **Disk:** each running in-process Gemma2 server holds about 314 MB in a temporary directory,
  removed at a normal JVM exit. A killed or crashed process (for example `SIGKILL`) leaves its
  directory behind, and on Windows files still open at exit may be left too; both wait for the OS's
  temp-folder cleanup.
- **Licence:** both pinned repos declare Apache-2.0 and neither is gated. Their licence link goes to
  Google's "Gemma 4 license" page, which holds the Apache License 2.0 text. Neither repo ships a
  LICENSE or NOTICE file. The jar therefore ships:
  - the Apache 2.0 text;
  - a NOTICE naming `google/embeddinggemma-2@914f7f89` and its ONNX conversion and quantisation,
    `onnx-community/embeddinggemma-2-ONNX@daa72c51`, with a statement that the files were modified;
  - a pointer to the Gemma Prohibited Use Policy, which both model cards say deployments "must
    adhere to".
- **Concurrency:** two runs on the same profile can still interleave their index writes. Each file write
  is atomic, so the last writer wins for each post. This is accepted until the HTTP transport arrives.

## Implementation status

As of 2026-10-11, on `feature/provider-distribution`:

**Done and green locally** (`./gw check pitest jarTest`, macOS arm64, with the Hugging Face mirror):
- All five modules and three distributions.
- The in-process provider, which `jarTest` exercises through a real MCP client with no model
  server.
- The LM Studio adapter, tested over real HTTP with WireMock.
- Most findings from the first review: the build property, lock, CI tests, old binaries, query
  errors, jar launch, passage-preparation version, nested builds, timeouts, tokenizer download,
  incompatible-model order and licence checks.
- CI runs `check pitest jarTest` on Linux only.

**Still open from the first review:**
- **Recipe revision (partly fixed).** The in-process provider's model ID names its ONNX revision. The
  LM Studio provider's model ID, and so its index recipe, is still the alias
  `text-embedding-embeddinggemma-2`, without the revision.
- **Startup failures.** Discovery and tokenizer failures are not yet `StartupFailure`. This is the
  same item as the review's suggestion below.
- **Distribution class loader (partly fixed).** It still loads classes parent-first in the
  in-process half of `ShadowJarTest`. The subprocess half, and the exact package and licence checks,
  use the jar itself.

**Open from the branch review (2026-10-11), which requested changes:**
- **Blocking: AC-SRCH-48's failure clause has no test.** That clause says a missing tokenizer fails
  Gemma2 startup, and `Gemma2TokenCounter` hard-wires its resource. The proposed fix is a
  package-private constructor taking the resource path, and one test.
- **Blocking: AC-SRCH-51's cleanup clause has no test.** That clause says the temporary model
  directory is removed at exit. The proposed fix is a jar test with its own `java.io.tmpdir` that
  checks no `blog-mcp-model-*` directory remains.
- **Suggested now:**
  - `OnnxEmbedder` takes the vector length from LangChain4j's output, so it no longer checks for
    384.
  - Provider discovery and tokenizer failures print a stack trace, not "blog-mcp cannot start".
  - Small fixes in `Gemma2OnnxModel`: close `SessionOptions`; close the session on the
    unexpected-inputs path; check that the expected inputs are present; return early from an empty
    `embedPassages`.
- **Suggested later:**
  - Remove `EmbeddingFactory`'s provider dimensions from the domain.
  - Add `PassageTokenBudget` boundary tests and its missing `body <= preparedPassage` rule.
  - Retire the test-only overloads, including the public `FileVectorIndex.open/4`, which writes to
    `search/index/`.
  - Pin the spike cosines in `Gemma2OnnxEmbedderTest`.
  - Take the model lock per passage rather than per post.
  - Small tidy-ups.
- **Deferred:**
  - `IndexFileLoader` guesses the vector dimension from the model ID or profile key.
  - `Embedding.similarityTo` does not check that both vectors have the same length.
  - Adopted legacy ONNX entries are re-embedded once.
  - Concurrency hardening waits for the HTTP transport (decision 7).
- **Not done:**
  - The parity test comparing in-process vectors with LM Studio's (`local-server`).
  - Windows and Linux arm64 are supported but not tested.
