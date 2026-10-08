# 9. Embedding-provider selection

Date: 2026-10-08

## Status

Proposed; awaiting owner approval. The build-time provider choice, separate ONNX/Gemma2 provider
artifacts, local serving alias and EmbeddingGemma 2 profile reflect owner decisions. The SPI placement,
Gradle property spelling, artifact assembly/classpath mechanism, token budgets and provider-keyed index
layout remain proposed structural details.

## Context

Search has two consumer-owned ports: `PassageEmbedder` in `search.domain.embedding` supplies the
model identity and passage vectors used by indexing, while `QueryEmbedder` in `search.application`
supplies query vectors. ADR 0006 split these ports by consumer. The selected provider must serve both
operations because vectors from different model spaces cannot be compared. Keep both ports separate;
no provider-selection aggregate or provider-specific type belongs in the domain.

ONNX BGE and Gemma2 are both separate provider JAR artifacts; neither provider implementation is
bundled into the base application artifact. The owner requires a build-time choice of exactly one
provider artifact, replacing the earlier runtime selector. The chosen provider is discovered from the
runtime classpath; there is no provider-selection environment variable. The owner confirmed that the
local OpenAI-compatible alias `text-embedding-embeddinggemma-2` serves Google EmbeddingGemma 2. Its
artifact may include or depend on tokenizer assets. The Google Hugging Face revision pinned for the
profile is `914f7f89142e33e77833254d9c9b90c3cef7303b`; official profile facts are 8,192 input
context tokens, native dimension 768, query prefix `task: search result | query: `, and document
format `title: {title} | text: {content}`, substituting `title: none` when a title is absent. The
revision's `tokenizer.json` is approximately 32.2 MB and can be used with the existing DJL 0.36.0
HuggingFace tokenizer. It is licensed Apache 2.0; redistribution must preserve its license and notices.

Each provider requires its own query preparation, passage preparation, tokenizer and token budget.
The existing ONNX BGE budget values are not generic defaults. Persisting every provider's index under
the same directory would let concurrent or separate model runs overwrite entries from another vector
space, so the index path must include a stable key for the selected indexing profile.

## Decision

1. Require a build property naming exactly one provider artifact. Propose Gradle property
   `embeddingProvider`, supplied as `-PembeddingProvider=onnx` or `-PembeddingProvider=gemma2`.
   Missing, blank or unsupported values fail Gradle configuration; there is no default. This
   build-time choice replaces, rather than coexists with, `BLOG_MCP_EMBEDDING_PROVIDER`; the app
   reads no provider-selection environment variable at runtime.
2. Provide the Gemma2 provider profile as built-in, pinned artifact configuration, including the model
   revision, tokenizer revision, model dimension, context limit, query/passage preparation and token
   budget. The provider artifact uses revision
   `914f7f89142e33e77833254d9c9b90c3cef7303b`, dimension 768 and supported context 8,192. Propose
   conservative passage limits of 3,000 body tokens, 256 overlap tokens, 128 title tokens and 4,096
   fully prepared passage tokens. These limits are all provider-specific, remain below the documented
   context, and must be checked against the artifact's pinned tokenizer. They do not inherit ONNX BGE's
   400/100/64/465 values. ONNX BGE retains its existing model-matched profile.
3. Build/distribute the base app with exactly the selected provider artifact on its runtime classpath.
   Both providers are separate JARs. Propose a Gradle configuration and distribution/start script
   which resolve the chosen provider and its runtime dependencies, preserving service descriptors;
   `ServiceLoader` discovery then finds exactly one provider. The app fails startup if discovery finds
   zero or multiple providers, or if the selected provider cannot load its pinned assets. No extra
   provider JAR path under the data directory and no provider-related runtime variable is used.
4. Place the proposed provider SPI in the existing `search.application` module/package boundary, for
   example `nz.sounie.blogmcp.search.application.embedding.spi`. It is a public host contract consumed
   by `app` and implemented by each provider artifact. It exposes a provider profile and one provider
   instance that implements the separate consumer-owned `PassageEmbedder` and `QueryEmbedder` ports,
   plus its tokenizer, budget, model identity and stable indexing-profile key. `app` discovers the
   selected JAR through the runtime classpath and composes it; neither the SPI nor either provider may
   depend on `app`, and neither search domain nor application depends on provider implementation
   code. This placement lets provider artifacts depend inward on the host search API without
   reversing the composition-root rule that nothing depends on `app`.
5. Instantiate the single selected provider instance and supply that same instance through both
   consumer-owned ports. Keep the port contracts separate. Query preparation and passage preparation
   remain independent: query prefix applies only to validated raw query text; document formatting
   only to canonical passage content.
6. Keep model-specific instructions, tokenizer classes/assets, context facts and budget configuration
   out of the domain. The selected `TokenCounter` and provider-neutral `PassageTokenBudget` are
   supplied to search through existing boundaries. Validate each fully prepared Gemma2 passage with
   the provider's tokenizer; tests verify its conservative limits are valid and provider-specific.
7. Give every indexing profile a stable directory key derived from its provider/model revision and
   indexing-profile version (including passage preparation and embedding-affecting budget/profile
   choices; exclude query-only preparation). Proposed paths are:
   - `search/index/onnx-bge-small-en-v1.5-q-v1/<site>/<FileKey(postId)>.json`
   - `search/index/gemma2-914f7f89-v1/<site>/<FileKey(postId)>.json`

   The keys are stable across restarts of the same profile and distinct between incompatible vector
   spaces. The composition root passes the selected key to `FileVectorIndex`; it does not add another
   path environment variable. Opening one profile reads and writes only its own subtree. Switching
   profiles neither loads, deletes, quarantines nor overwrites the other profile's entries. Preserve
   the existing per-file atomic writes, stored recipe/fingerprint checks and reconcile behavior within
   the selected subtree. A profile-key change for embedding-affecting configuration selects a new
   subtree; ordinary content changes continue to reconcile and rewrite entries for that profile.
   For existing default ONNX data at the legacy `search/index/<site>/<post>.json` path, propose a
   one-time adoption into the ONNX keyed subtree before opening it. A Gemma2 selection must not inspect
   or move those legacy files.
8. Keep provider identity and passage-recipe validation in index files. No domain event is emitted for
   provider selection: it is process-start composition, not a business state transition.

## Provider-specific facts and limits

- ONNX BGE keeps its BGE query preparation, tokenizer and 400 body / 100 overlap / 64 title / 465
  fully prepared passage token limits. Those limits are not defaults for other providers.
- Google EmbeddingGemma 2 uses the pinned revision and profile stated above. Its query and document
  inputs are exactly `task: search result | query: {query}` and
  `title: {title} | text: {content}` (with `title: none` when absent). The provider tokenizer comes from
  that same revision. Model and tokenizer facts are established by the pinned official model assets,
  not inferred from the OpenAI-compatible alias.
- The tokenizer asset is approximately 32.2 MB. The Gemma2 provider artifact may carry that asset
  and/or depend on the existing DJL HuggingFace tokenizer library. Preserve Apache 2.0 attribution and
  notices when packaging or redistributing it.

## Rejected alternatives

- Merge `PassageEmbedder` and `QueryEmbedder` into one provider port: that reverses ADR 0006's
  consumer-owned-port decision.
- Make a provider artifact or SPI depend on `app`, or put the SPI in `app`: providers would then depend on the
  composition root and violate inward dependency direction.
- Add runtime provider/profile environment variables: the owner chose build-time selection and
  provider artifacts own their pinned profiles/defaults.
- Reuse the ONNX BGE preparation, tokenizer or token budget for Gemma2: preparation and token counts
  are provider-specific.
- Treat a missing provider on the runtime classpath as a fallback to another model: this hides a
  packaging error and risks comparing incompatible vectors.
- Store all providers' indexes at `search/index/<site>/...`: different models could overwrite one
  another, and concurrent model runs would not be isolated.

## Consequences

- Exactly one provider artifact is required at build time and on the resulting runtime classpath.
  There is no implicit ONNX default and no runtime provider selector.
- The proposed host SPI belongs at the search application boundary. `app` owns provider discovery and
  composition; both provider artifacts are independent of `app` and are not imported by domain code.
- The profile's query/passage strategies, tokenizer, dimension and budget travel together, while the
  two public consumer ports stay separate.
- Profile-keyed index subtrees allow concurrent runs on different embedding profiles and stable
  restart reuse without cross-profile deletion or overwrite. Content/recipe reconciliation remains
  unchanged within each profile.
- No domain event or provider-selection environment variable is introduced.

## Acceptance criteria

- AC-SRCH-40–45 cover required build selection, classpath discovery/failure, separate ports, one
  selected provider instance and embedding compatibility.
- AC-SRCH-46–49 cover provider-specific preparation, built-in Gemma2 profile and budget validation,
  artifact discovery and missing-asset failure.
- AC-APP-47 and AC-SRCH-50 cover stable profile-keyed paths, same-profile restart reuse and profile
  switching without cross-profile loads, deletes or overwrites.

## Open questions before implementation

- Approve the SPI's exact public API and package/module placement under `search.application`.
- Confirm the Gradle property name, provider-artifact configuration, classpath assembly/distribution
  and launcher mechanism. Build-time selection is required and there is no runtime selector.
- Confirm the Gemma2 default budgets after running the pinned tokenizer against representative and
  pathological passages; keep the final fully prepared limit conservative relative to 8,192.
- Confirm how the Gemma2 provider artifact reaches the local OpenAI-compatible endpoint without adding
  runtime provider/profile environment settings (or whether endpoint configuration belongs in its
  built-in profile/configuration).
- Confirm the one-time legacy ONNX index adoption and its atomic move/copy semantics. Once opened at
  the selected profile path, subsequent restarts must reuse it; Gemma2 must not touch the legacy path.
- Different profiles can write concurrently to separate subtrees. Same-profile concurrent app
  instances share a subtree; decide whether the deferred data-directory lock in ADR 0007 is required
  before supporting that deployment.
- Confirm provider dependency packaging and tokenizer license/notice handling in the Gemma2 artifact.

## Sources

- Google EmbeddingGemma 2 model card and files at pinned revision:
  <https://huggingface.co/google/embeddinggemma-2/tree/914f7f89142e33e77833254d9c9b90c3cef7303b>
- Pinned tokenizer configuration:
  <https://huggingface.co/google/embeddinggemma-2/blob/914f7f89142e33e77833254d9c9b90c3cef7303b/tokenizer.json>
- OpenAI embeddings request contract:
  <https://developers.openai.com/api/reference/resources/embeddings/methods/create>
- LM Studio OpenAI-compatible embeddings documentation:
  <https://lmstudio.ai/docs/developer/openai-compat/embeddings>
