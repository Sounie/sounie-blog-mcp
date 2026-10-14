# Plan: image content in Gemma2 search

This is the living delivery plan for [ADR 0011](../adr/0011-image-embeddings-for-gemma2.md). It is
the single source of truth for progress across sessions. Each phase's PR updates the status table.

**Goals**
1. Index the images inside each blog post next to its text chunks, so text queries can match what
   the images show.
2. Search by image: given a local file path or an HTTPS URL, return the posts that are most similar.

## Status (update at the end of every phase)

| Phase | Branch | State | Notes |
|---|---|---|---|
| 0 Spike and ADR | `feature/image-embeddings-spike` | Done | Merged as #20. Reference run 2026-10-13: q8 images 0.99909 to 0.99981 vs official, 6 of 6 rankings. ADR 0011 accepted |
| 1 Provider image embedding | `feature/gemma2-image-embedding` | Done | Merged as #22. AC-SRCH-53 to 65 done; Java vs official 0.99908 to 0.99980, 6 of 6 rankings |
| 2 Catalog post images | `feature/post-images` | Done | On `main` as 2e3d3d8, 55d78da, 153fdc3. AC-CAT-33 to 41 done; format 1 read as no images (Q9); https only (Q10) |
| 3 Search indexes images | `feature/search-image-entries` | Done | Merged as #26. AC-SRCH-66 to 80 done; no host allow-list, image addresses must be permitted by the post host's address category. Follow-up #28: shutdown waits for a running embedding (no SIGSEGV on exit), a startup reconcile, and reliable rejection of cut image bodies. By-hand "black swan" check passed 2026-10-14 |
| 4 Search by image | `feature/find-posts-like-image` | Not started | Next |

## Fixed decisions (don't reopen without the owner)

- **Reference.** Google's official `google/embeddinggemma-2@914f7f89…` weights in fp32 PyTorch,
  with mean pooling and L2 normalisation, recorded in
  `core/src/test/resources/gemma2-image-reference.json`.
- **Runtime.** ONNX Runtime Java in the in-process `gemma2` provider, with the q8
  `vision_encoder_quantized` (195 MB). Use fp32 (671 MB) only if q8 misses the parity threshold.
  - `gemma2-server` (LM Studio) and `onnx` (BGE) stay text-only. No server or hosted runtime
    documents image embedding for this model (research, 2026-10-13).
- **Preprocessing.** A Java port of transformers' `image_processing_pil_gemma4.py`:
  - Scale both sides to fit 280×9 patches of 16 px, then floor each to a multiple of 48.
  - Resize with a port of PIL's antialiased bicubic.
  - Rescale by 1/255, with no normalisation.
  - Pad to 2,520 patches: pixels with 0, positions with −1.
  - Input ids are `[2, 255999, 258880×n, 258882, 1]`.
  - Images take no task prefix.
- **Scope.** Only images inside the post content are indexed: WordPress `content.rendered` and
  Blogger `content.$t`. Navigation, headers and sidebars never appear in those fields.
  - Excluded: emoji and smileys, images smaller than 64 px, tracking pixels, `data:` URIs, non-HTTPS
    URLs, duplicates.
- **Index entries.** Each image is embedded alone, as its own entry beside the post's text chunks,
  and a post scores as its best entry.
  - An image match's snippet is its alt text, then its caption, then `Image: <url>`.
- **Query image input.** An absolute local path or an `https://` URL, through a separate tool,
  `find_posts_like_image`.
- **Untrusted input.**
  - Post images are fetched only over HTTPS (owner decision, 2026-10-14: no host allow-list,
    because blogs may serve images from arbitrary domains of their own). Images may come from any
    https host whose address category is permitted by the category of the post's host:
    - a public blog permits only public image addresses;
    - a private-network blog also permits private-network addresses;
    - link-local, any-local and multicast addresses are fetched only for a blog whose own host is
      in the same non-routable category.

    DNS rebinding is an accepted residual risk.
  - Query URLs must not resolve to loopback, link-local or private addresses.
  - At most 10 MB, with content-type checks and timeouts.
  - Supported formats are JPEG, PNG and GIF. WebP is skipped with a warning.

## Phase 0: spike and ADR (`feature/image-embeddings-spike`)

**Done (uncommitted)**
- Six fixtures in `core/src/test/resources/`, 1024 px with no metadata.
- `scripts/gemma2-image-reference.py`, rewritten for the official weights.
- The ADR 0011 draft.
- `.gitignore` entries for `.hf-mirror/`, `.venv-spike/` and `__pycache__/`.
- The `ms-elegant` site added to `docs/domain/app.md`.
- The official-weights reference, `core/src/test/resources/gemma2-image-reference.json` (2026-10-13).
  The owner ran it outside the sandbox, since it needs huggingface.co.
- The ADR's Parity section, filled in:
  - q8 image embeddings measured 0.99909 to 0.99981 against official, and queries 0.99995 to
    0.99998;
  - all 6 queries ranked their intended image first;
  - the Java threshold is set at ≥ 0.994;
  - q8 is kept and no fp32 fallback is needed.

- ADR 0011 accepted by the owner on 2026-10-13.

**Remaining**
1. The owner reviews and merges the phase 0 PR.

**Exit:** ADR 0011 is Accepted, with real numbers, and the PR is merged.

## Phase 1: image embedding in the `gemma2` provider (`feature/gemma2-image-embedding`)

**Scope**
- Pin `vision_encoder_quantized.onnx` and `.onnx_data` in `providers/gemma2/build.gradle.kts`, in
  the `huggingFaceAssets` configuration next to `model_quantized`, using the same Ivy pattern.
  - Copy them into `embeddinggemma-2-onnx/`.
  - Regenerate checksums with `scripts/update-verification-metadata`; never edit them by hand.
- Core SPI:
  - New port `ImageEmbedder` in `search.domain.embedding`, next to `PassageEmbedder` (owner
    decision, 2026-10-13): `Embedding embedImage(ImagePixels)`.
  - `ImagePixels` is a value object that validates width, height and RGB data.
  - `EmbeddingProvider` (`core/.../search/application/embedding/spi/EmbeddingProvider.java`) gains
    `Optional<ImageEmbedder> imageEmbedder()`. `onnx` and `gemma2-server` return empty.
- `providers/gemma2/.../onnx/`:
  - `Gemma2ImagePreprocessor` (pure Java), with steps separated for testing:
    - `TargetSize`, which floors to a multiple of 48;
    - `PilBicubicResize`, which ports PIL's two-pass convolution with support scaled by the
      downscale factor;
    - `Patchifier`, which builds the patches, position ids and padding.
  - A vision session alongside `Gemma2OnnxModel`.
  - `Gemma2OnnxModel` feeds real `image_features` in place of the `NO_MEDIA` empty tensor for image
    input. Text calls are unchanged.
  - Reuse `BundledModelFiles` for extraction and the existing lazy load and lock in
    `Gemma2OnnxEmbedder`.
- **Tests**
  - Unit tests for `TargetSize` (4:3 → 912×672, portrait → 672×912, tiny and extreme aspect ratios)
    and for the patchifier.
  - `@Tag("model")` parity tests against the reference JSON:
    - `inputIds`, `patchGrid` and `realPatches` exact;
    - `pixelValuesSum` within tolerance;
    - cosine against the official embedding at or above the ADR threshold;
    - 6 of 6 cross-modal rankings.
  - ACs AC-SRCH-53 onward in `docs/domain/search.md`.

**Exit:** `./gw check pitest` is green, and
`./gw :providers:gemma2:test -PhuggingFaceMirror=.hf-mirror` passes the parity tests.

**Dependencies:** every build, test and runtime dependency of the image path is listed in
[gemma2-vision-dependencies.md](../reference/gemma2-vision-dependencies.md).

## Phase 2: catalog records post images (`feature/post-images`)

**Scope**
- Glossary in `docs/domain/catalog.md`: **Post image** (URL, alt text, caption). ACs from AC-CAT-33.
- `PostImage` value object, with an HTTPS URL and optional alt text and caption. `PostContent` gains
  `List<PostImage>`, and `RevisedAspect` gains `IMAGES`.
- `PostImageExtractor` lives in `catalog/adapter/out`, next to `JsoupHtmlToText`. It's a rule table
  of exclusions, and resolves `srcset` to the largest candidate within 2,048 px.
  - It is called from `WordPressPostMapper` and `BloggerEntryMapper` on the same HTML they already
    pass to `htmlToText`.
- Carry the images through every layer:
  - `PostSnapshot` and `PostPublished`/`PostRevised`;
  - `IntegrationEvents` and `CatalogPostPublished`/`CatalogPostRevised` in `shared.event`, as string
    fields;
  - `CatalogPostState`;
  - `PostFile`.
- `StoredFormat.CURRENT` becomes 2. Format 1 files go through the existing quarantine-and-refetch
  path. Confirm with the owner that a full refetch on upgrade is acceptable.

**Exit:** `./gw check pitest` is green. A WireMock fixture with an emoji, a 1×1 pixel and a real
photo yields exactly one post image.

## Phase 3: search indexes images (`feature/search-image-entries`)

**Scope**
- `PostToIndex` gains its images, mapped in `SharedCatalogPosts`.
- New port `ImageSource.fetch(URI imageUrl, URI postUrl)`, with adapter `HttpImageSource` in
  `search/adapter/out`. The port lives in `search.domain.image`, not `search.application`, because
  `PostIndexer` calls it (owner decision Q-IMG 5, 2026-10-14).
  - It fetches from any https host: there is no host allow-list (owner decision, 2026-10-14).
  - The catalog stores image URLs on any https host (catalog invariant 7). So `HttpImageSource`
    checks every resolved address of the image host, and of each redirect target, against the
    category of the post's host (`AddressCategory`; search.md 3.7). A public blog permits only
    public image addresses, and a private-network blog also permits private-network addresses.
    Link-local, any-local and multicast addresses are fetched only for a blog whose own host is in
    the same non-routable category. This came from the phase 2
    review.
  - It drops decoded images under 64 px, which catches tracking pixels with no declared size
    (catalog Q13).
  - It enforces the size and content-type caps and decodes with `ImageIO`.
  - It is tested with WireMock.
- Domain:
  - `IndexedImage(index, url, description, Embedding)` in `IndexedPost`. `bestMatch` covers both
    kinds of entry.
  - `Snippet` handles image entries.
  - `IndexRecipe` gains an `images-v1` part, and `ContentFingerprint` includes the image URLs.
  - `PostIndexer` embeds images through `ImageEmbedder` when the provider has one. A failed image
    skips only that image and is retried on the next reconcile.
- `IndexFile` and `IndexFileLoader` move to format 2, with an `images` array of url, description
  and vector.
- MCP output: an optional `matchedImage` in `ToolDefinitions` and `SearchResultView`.

**Exit:** `./gw check pitest shadowJar jarTest` is green. By hand, the gemma2 jar against
`sounie-wp` answers "black swan" with the post whose snippet comes from its image.

## Phase 4: search by image (`feature/find-posts-like-image`)

**Scope**
- `ImageQuery` is sealed: `LocalImage(Path)` (absolute, regular file, image extension, at most
  10 MB) or `RemoteImage(URI)` (HTTPS, not resolving to a private address).
- A use case `FindPostsLikeImage` reuses the filtering and ranking in `SearchPosts`.
- MCP tool `find_posts_like_image` takes `image`, plus `site`, `from`, `to` and `limit` as in
  `search_posts`. It has its own argument reader, modelled on `SearchPostsArguments`.
  - It is registered in `BlogMcpServer` only when the provider has an image embedder.
- Docs: `docs/domain/app.md` and `search.md` ACs.

**Exit:** `./gw check pitest shadowJar jarTest` is green, and the jar test finds the swan post from
`black-swan-in-reeds.png`.

## Resuming in a new session

1. Read this document and ADR 0011, then `git status` and `scripts/git-remote fetch`.
2. Take the first phase whose state isn't Done. Start it with `/feature <phase summary>` on its
   branch, which you create from an up-to-date `main`.
3. At the end of a phase, update the Status table and Notes in the same PR.
