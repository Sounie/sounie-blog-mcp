# Search bounded context

Status: Existing model decisions approved by the owner on 2026-10-02. Embedding providers (ADR 0009,
accepted 2026-10-09, and ADR 0010, accepted 2026-10-11) are implemented on
`feature/provider-distribution`: one Gradle module and jar per provider, SPI discovery,
provider-specific profiles and budgets, and profile-keyed index paths under `search/indexes/`. The
branch is under review; the open items are listed in ADR 0010's implementation status.
Image embedding in the in-process `gemma2` provider (ADR 0011, phase 1; glossary "Image pixels" to
"Vision encoder", AC-SRCH-53 to 65) was approved by the owner on 2026-10-14
(`feature/gemma2-image-embedding`). It adds a port and a value object only; no aggregate, index, catalog or MCP behaviour changes.
Indexing post images (ADR 0011, phase 3; section 3.7, glossary "Post image (search)" to "Matched
image", AC-SRCH-66 to 80) was **approved by the owner on 2026-10-14** (`feature/search-image-entries`).
Package: `nz.sounie.blogmcp.search`
Feature tag for acceptance criteria: `SRCH`

## 1. Purpose

Search turns the catalog's posts into a **local semantic index** and answers natural-language
queries against it. It ranks **posts**, not fragments: each result is one post with its best-matching
passage as a snippet.

Search is responsible for:
- indexing only posts whose body is the **full** text. Posts the catalog holds only as a `SUMMARY`
  are excluded (owner decision, Q7);
- splitting each post into overlapping **chunks** that stay safely under the embedding model's input limit;
- turning each chunk, together with the post title, into an **embedding** with the provider in the
  distribution being run: ONNX BGE, Google EmbeddingGemma 2 in process, or EmbeddingGemma 2 served by
  LM Studio;
- keeping the index in step with the catalog: reacting to the catalog's integration events, and
  **reconciling** against the catalog's current posts to repair missed events or a changed recipe;
- answering a **search query** with optional site and published-date filters and a clamped limit.

It is **not** responsible for:
- fetching, storing or changing posts. The catalog owns them, and search only mirrors what it needs;
- returning full post bodies (`get_post` is the catalog's `GetPost`);
- the MCP protocol, the scheduler, `Main`, or **file persistence of the index**. All of these are
  slice 3. In this slice the `VectorIndex` port has only an in-memory implementation, so the index
  is rebuilt by a reconcile on every start until slice 3 adds file persistence next to the catalog's file
  repositories.

Search **never imports `catalog`** (ArchUnit `search_does_not_depend_on_catalog`). It learns about
posts only through the published language in `nz.sounie.blogmcp.shared` (ADR 0003, ADR 0005).

## 2. Ubiquitous language

| Term | Meaning | Code name |
|---|---|---|
| Invalid provider build selection | Retired by ADR 0010: the build no longer takes a provider property. | (none) |
| Post ID | Search's own copy of the catalog's post identity, in the external form `<siteId>:<sourcePostId>`. Parsed from the published language, and never shared as a catalog type. | `PostId` (search.domain) |
| Site ID | The site part of a post ID (`[a-z0-9-]{1,40}`). It is used for the site filter. | `SiteId` (search.domain) |
| Completeness | Whether the catalog has the `FULL` post text or only a `SUMMARY`. It is also the **indexability rule**: `FULL` posts go through the normal index decision, and `SUMMARY` posts are always excluded (owner decision, Q7). It is not stored in the index and not shown in results, because everything indexed is `FULL`. | `Completeness` (enum with behaviour `decide(...)`) |
| Excluded (post) | A catalog post that must not be in the index because its completeness is `SUMMARY`. Excluding a post removes any entry it already has. | `IndexDecision.Exclude` |
| Post to index | The catalog's current state of one post, translated into search language: post ID, completeness, post metadata, title, body and (phase 3) its **post images** in catalog order. This is the single input to every index decision, whether it came from an event or from a reconcile. | `PostToIndex` |
| Post metadata | Everything about an indexed post that search stores but does **not** embed: site ID, canonical URL, title, tags, published at and updated at. A difference in metadata alone never triggers re-embedding. | `PostMetadata` |
| Word sequence | The post body after **text normalisation**, as an ordered list of words. Any run of Unicode whitespace (spaces, tabs, newlines, no-break space U+00A0) separates words. Any **overlong word** is split into pieces. | `WordSequence` |
| Overlong word | A whitespace-free run longer than 64 characters, such as a URL, base64 data or minified code. It is split into consecutive pieces of at most 64 characters, which then count as separate words. | `WordSequence` (rule `MAX_WORD_CHARS = 64`) |
| Token | One unit counted by the selected provider's configured tokenizer. Tokenization and context limits are provider-specific; a provider-neutral budget must be selected with the provider. | `TokenCounter` (domain port) |
| Passage token budget | Provider-selected, provider-neutral limits for chunk body tokens, overlap tokens, title tokens and fully prepared passage tokens. It is applied with the selected provider's tokenizer; vendor/model details stay outside the domain. The Gemma2 limits are built into its pinned provider profile, not runtime environment settings. | `PassageTokenBudget` (domain policy/configuration) |
| Token counter | Port that counts the selected provider's actual tokens. Provider tokenizer assets are pinned with the provider profile; an OpenAI-compatible endpoint cannot prove which model revision it loaded. | `TokenCounter` (domain port), provider adapter (`search.adapter.out`) |
| Embedding provider | The one provider in a distribution supplies a single provider instance, tokenizer and budget. There are three, each its own module: ONNX BGE, EmbeddingGemma 2 in process (q8 ONNX), and EmbeddingGemma 2 served by LM Studio under the owner-confirmed `text-embedding-embeddinggemma-2` alias. | `EmbeddingProvider` implementations, discovered from the classpath (`OnnxEmbedder`, `Gemma2OnnxEmbedder`, `Gemma2Embedder`) |
| Embedding provider selection | Choosing which provider distribution to run. Each provider module builds its own jar containing exactly one provider. There is no build property, runtime selector or implicit default. | One Gradle module and jar per provider (ADR 0010) |
| Provider model identity | The model and pinned revision identity exposed through the passage-embedding port and recorded in the index recipe/profile key. The LM Studio provider records Google's revision `914f7f89142e33e77833254d9c9b90c3cef7303b`; the in-process provider records the ONNX conversion `onnx-community/embeddinggemma-2-ONNX@daa72c51` and its q8 quantisation. | `PassageEmbedder.modelId()` |
| Gemma2 profile | The profile both Gemma2 providers share: pinned tokenizer revision, dimension 768, context 8,192, the Gemma2 prompts and conservative token limits. Each provider adds its own model revision and index profile key. No Gemma2-specific runtime variables are required. | `Gemma2Profile` (`providers/gemma2-common`) |
| Shared provider instance | Exactly one instance from the distribution's provider implements both separate consumer-owned embedding ports, ensuring passage and query vectors inhabit the same model space. | `EmbeddingProviderDiscovery` and `Adapters` (`app`) |
| Query preparation | Provider-selected transformation of the raw `QueryText` into the exact query input the model expects. It is separate from query validation and from passage preparation; the selected adapter applies it, so query text in the domain carries no provider-specific prefix. | `EmbeddingProvider.prepareQuery` |
| Passage preparation | Provider-selected transformation of canonical passage content into the exact model input, including any required instruction/template. Its version and budget affect the index recipe; its implementation remains in the adapter, which also declares its version. | `EmbeddingProvider.preparePassage`, `passagePreparationVersion()` |
| Chunk | A contiguous window of the word sequence that is embedded as one unit. A chunk has a zero-based **chunk index** in body order, and its text is its words joined by single spaces. | `Chunk` |
| Chunking policy | The provider-neutral rule that decides chunk boundaries and overlap. Its token limits come from the selected `PassageTokenBudget`; they are not assumed to be shared by different providers. | `ChunkingPolicy` |
| Overlap | The trailing words of one chunk that are repeated at the start of the next, so that a sentence crossing a boundary appears whole in at least one chunk. | `ChunkingPolicy` |
| Passage | Canonical content for one chunk: title and chunk text. The selected provider's passage-preparation strategy converts it to exact model input. | `Passage` |
| Title line | The canonical title content, limited according to the selected `PassageTokenBudget`; the adapter may apply its provider's passage template afterward. | `PassageComposition` |
| Passage composition | The rule that builds passages from a title and chunks. | `PassageComposition` |
| Embed | Turn canonical passage content or raw query text into an embedding through the selected provider and its input-preparation strategies. | `PassageEmbedder.embedPassages(...)`, `QueryEmbedder.embedQuery(QueryPassage)` |
| Embedder | The selected provider instance is seen through two ports split by consumer (ADR 0006): the **passage embedder** (`modelId()` and `embedPassages(List<Passage>)`, used by indexing) and the **query embedder** (used by search). The same instance implements both ports and applies the provider's independent query and passage preparation. | `PassageEmbedder` (`search.domain.embedding`), `QueryEmbedder` (`search.application`) |
| Embedding | A vector whose dimension matches the explicitly configured model, with finite values and unit length. | `Embedding` |
| Image pixels | A decoded image as an 8-bit RGB raster: a width and a height in pixels, and `width × height × 3` bytes, row-major from the top-left pixel, channels interleaved R, G, B, each read as unsigned 0..255. Alpha, palettes and greyscale are already resolved by whoever decodes the file. It is immutable: the bytes are copied in and copied out. Both sides are at least 1, and the image has at most 16,777,216 pixels (4,096 × 4,096). See invariants in 3.6. | `ImagePixels` (`search.domain.embedding`) |
| Invalid image pixels | The rejection of an image-pixels input, with reason `NON_POSITIVE_DIMENSION`, `TOO_MANY_PIXELS` or `DATA_LENGTH_MISMATCH`. | `InvalidImagePixels` (`search.domain.embedding`) |
| Image embedder | Port that turns image pixels into an embedding in the **same vector space** as the provider's query and passage embeddings. Images take no task prefix. It throws `EmbedderUnavailable` if the model cannot embed. | `ImageEmbedder.embedImage(ImagePixels)` (`search.domain.embedding`) |
| Image capability | Whether the distribution's provider can embed images. A provider declares it by returning an image embedder or nothing. Only the in-process `gemma2` provider has one (ADR 0011); `onnx` and `gemma2-server` are text-only. Asking never loads a model. | `EmbeddingProvider.imageEmbedder()` returning `Optional<ImageEmbedder>` |
| Similarity | The cosine similarity of two embeddings. Because embeddings are unit length, this is their dot product. Its range is [-1, 1], and higher means more related. | `Similarity` |
| Index recipe | The identity of the method used to build an index entry: selected model and tokenizer revision identity, chunking and selected passage-budget parameters, passage-preparation/template version, and (phase 3, image indexing only) the image part `images-v1`. The tokenizer filesystem path and supported context are deployment/validation details, not recipe identity. Query preparation is excluded because it does not change stored passages. Changing an indexing-relevant part makes every entry stale. | `IndexRecipe`; image part added by `ImageIndexing.appliedTo(IndexRecipe)` |
| Content fingerprint | SHA-256 over the index recipe, the normalised title, the normalised body and (phase 3, image indexing only) each **settled image**'s URL and description, in catalog order. With no images it is exactly the text-only fingerprint. If two posts have the same fingerprint, they produce the same entries. Tags and other metadata are **not** included. | `ContentFingerprint` |
| Indexed post | The aggregate: one post's metadata, its content fingerprint, its indexed chunks and (phase 3) its indexed images. | `IndexedPost` |
| Indexed chunk | A chunk's index and text together with its embedding. | `IndexedChunk` |
| Post image (search) | (phase 3) Search's own copy of one catalog post image: an absolute `https` URL with a host, alt text and caption (plain text, empty when absent, never null). Parsed from the published language; never a catalog type. | `PostImage` (`search.domain.image`) |
| Image description | (phase 3) The text that stands for an image in results: its alt text if not blank, else its caption if not blank, else `Image: <url>`. Whitespace-normalised like a chunk. It is never blank. It is **not** embedded: the image is embedded from its pixels alone. | `PostImage.description()` |
| Image source | (phase 3) Domain port that fetches one post image's pixels, given the image URL and the post's canonical URL (whose host decides the **blog category**). It never throws for an expected outcome; it returns an **image fetch**. The domain knows nothing about addresses; that is the adapter's concern. | `ImageSource.fetch(URI imageUrl, URI postUrl)` (`search.domain.image`), `HttpImageSource` (`search.adapter.out`) |
| Image fetch | (phase 3) The outcome of fetching one image. One of three variants:<br>• **fetched**: the decoded image pixels;<br>• **rejected**: the image itself can never be indexed (an **image rejection** reason), so retrying is pointless;<br>• **unavailable**: a transient failure (timeout, connection error, server error), so it is worth retrying. | `ImageFetch` (sealed: `Fetched(ImagePixels)`, `Rejected(ImageRejection)`, `Unavailable(String detail)`) |
| Image rejection | (phase 3) Why an image can never be indexed: `NOT_HTTPS` (the URL, or a redirect target, is not `https`), `ADDRESS_NOT_PERMITTED` (an address of the image host, or of a redirect target, is outside what the blog category permits), `TOO_MANY_REDIRECTS`, `HTTP_STATUS` (a 4xx other than 408 and 429), `TOO_LARGE` (over 10 MiB, or over the image-pixels cap), `UNSUPPORTED_TYPE` (not JPEG, PNG or GIF, including WebP), `UNDECODABLE` or `TOO_SMALL` (a decoded side under 64 px). | `ImageRejection` (enum, `search.domain.image`) |
| Settled image | (phase 3) A post image whose fate is final for this content: it was either embedded or rejected. Only settled images go into the stored fingerprint. | `ImageAttempt` (`Indexed`, `Skipped`) |
| Pending image | (phase 3) A post image that was unavailable, or whose embedding failed. It is not recorded and not settled, so the stored fingerprint differs from the current one and the next upsert of the post (the next reconcile at the latest) re-embeds the post and tries again. | `ImageAttempt.Pending` |
| Image indexing | (phase 3) The strategy, chosen once at composition, for a post's images: **text-only** (no fetching, recipe and fingerprint exactly as before phase 3) when the provider has no image embedder, or **with images** (fetch, embed, recipe part `images-v1`) when it has one. Nothing else asks which one it is. | `ImageIndexing` (sealed: `TextOnly`, `WithImages(ImageSource, ImageEmbedder)`, `search.domain.index`) |
| Indexed image | (phase 3) One embedded post image: its **image index** (its zero-based position in the post's images, so skipped images leave gaps), URL, image description and embedding. | `IndexedImage` (`search.domain.index`) |
| Index entry | (phase 3) Either an indexed chunk or an indexed image: anything a query is scored against. | `IndexEntry` (sealed: `IndexedChunk`, `IndexedImage`) |
| Image host allow-list | Not used (owner decision, 2026-10-14): blogs may serve images from arbitrary domains of their own, so post images may come from **any** `https` host. The blog category, the redirect limit and the size, type and pixel limits are what protect the fetch. | (none) |
| Address category | (phase 3) Where an address lives, recognised by a rule table that also handles IPv4-mapped IPv6 forms:<br>• `PUBLIC`: every address not in the categories below;<br>• `PRIVATE_NETWORK`: 10/8, 172.16/12, 192.168/16, 100.64/10 (CGNAT, e.g. Tailscale) and `fc00::/7`;<br>• `LOOPBACK`: 127/8 and `::1`;<br>• `NON_ROUTABLE`: link-local 169.254/16 (cloud metadata) and `fe80::/10`, any-local, and multicast.<br>Each category owns `permits(AddressCategory)`, which says which image addresses a blog in that category may use: `PUBLIC` permits `PUBLIC`; `PRIVATE_NETWORK` permits `PUBLIC` and `PRIVATE_NETWORK`; `LOOPBACK` permits `PUBLIC`, `PRIVATE_NETWORK` and `LOOPBACK`; `NON_ROUTABLE` permits `PUBLIC` and `NON_ROUTABLE`. Only a `NON_ROUTABLE` blog permits `NON_ROUTABLE` images, so a link-local, any-local or multicast image address is fetched only for a blog whose own host is in that same category (owner decision, 2026-10-14). | `AddressCategory` (enum, `search.adapter.out`, package-private), `AddressCategory.of(InetAddress)` |
| Blog category | (phase 3) The address category of the host of the post's canonical URL, resolved at fetch time. If that host has several addresses, it is the category among them that permits the least, so a host with any public address counts as `PUBLIC`. (`PRIVATE_NETWORK` and `NON_ROUTABLE` permit sets that neither contains the other; a host with addresses in both permits only what both permit, i.e. `PUBLIC`.) Every resolved address of the image host, and of each redirect target, must be permitted by it. A post host that does not resolve makes the fetch `Unavailable`. No site configuration flows into search. | `HttpImageSource` (using `AddressCategory`) |
| Vector index | Port holding every indexed post. In this slice it has an in-memory implementation only, and file persistence is slice 3. | `VectorIndex` (domain port), `InMemoryVectorIndex` (adapter.out) |
| Index change | What an integration event asks of the index: **upsert** a post (for both a publish and a revise) or **remove** a post (for a withdraw). | `IndexChange` (sealed: `Upsert`, `Remove`) |
| Index decision | What must happen to one post's entry: **add** (not indexed yet), **re-embed** (the fingerprint differs), **refresh metadata** (same fingerprint, different metadata), **keep** (nothing differs), **exclude** (a summary-only post), **remove** (withdrawn, or an orphan) or **unreadable** (a malformed catalog post whose ID is known: leave its entry untouched and report it as failed). Readable posts go through one entry point, `IndexDecision.forPost(...)`, for both events and reconciles. | `IndexDecision` (sealed: `Add`, `ReEmbed`, `RefreshMetadata`, `Keep`, `Exclude`, `Remove`, `Unreadable`) |
| Index outcome | The result of applying one index decision: `ADDED`, `RE_EMBEDDED`, `METADATA_REFRESHED`, `UNCHANGED`, `REMOVED` (an entry was deleted, whether by `Remove` or `Exclude`), `ALREADY_ABSENT` (a `Remove` with no entry), `EXCLUDED` (an `Exclude` with no entry) or `FAILED` (embedding failed, or the decision was `Unreadable`). | `IndexOutcome` |
| Index a post | Apply one index change: decide, then (if needed) chunk, compose, embed and save the whole entry. | `IndexPost` use case |
| Post catalog | Search's port for reading the catalog's current posts as **catalog entries**. It is implemented over the shared query contract `CatalogPosts`. | `PostCatalog` (application port), `SharedCatalogPosts` (adapter.out) |
| Catalog entry | One item of the catalog's current posts as search reads it during a reconcile. It is one of three variants:<br>• **readable**: it translates to a post to index;<br>• **unreadable**: the post ID parses, but something else is malformed (including a null field), carrying the post ID and a reason;<br>• **unidentified**: the post ID itself cannot be read, carrying a reason.<br>A malformed state is never silently dropped (review B1). A post ID listed more than once is planned as a single unreadable decision whose reason mentions "duplicate". | `CatalogEntry` (sealed: `Readable(PostToIndex)`, `Unreadable(PostId, reason)`, `Unidentified(reason)`) |
| Reconcile (the index) | Compare the catalog's current posts with the vector index, then add the missing ones, re-embed the stale ones, refresh changed metadata, exclude summary-only ones and remove orphans. A **rebuild** is a reconcile against an empty index, so there is no separate code path. | `ReconcileIndex` use case, `ReconcilePlan` |
| Orphan | An indexed post whose post ID is not among the catalog's current posts. The post IDs of **readable and unreadable** entries both count as current, so a malformed catalog post is never an orphan. Orphans are removed only when the run has **no unidentified** entry. Otherwise orphan removal is suppressed for that run, because any orphan might be the unidentified post. | `ReconcilePlan` |
| Stale entry | An indexed post whose content fingerprint differs from the one computed for the catalog's current state (because of a content change or a recipe change). | `IndexDecision.ReEmbed` |
| Reconcile report | Counts by index outcome, plus the post IDs that failed. Each failed post carries its **reason**: the embedder error, the malformation of an unreadable entry, or a duplicate post ID in the catalog listing. The report also says whether **orphan removal was suppressed** (warning `ORPHAN_REMOVAL_SUPPRESSED`, with the reasons of the unidentified entries), mirroring catalog AC-CAT-23. | `ReconcileReport` |
| Search query | Query text, a site filter, a published-date range and a result limit. Valid only as a whole. | `SearchQuery` |
| Query text | The trimmed text of the query. It must not be blank and is at most 1,000 characters. | `QueryText` |
| Site filter | `AnySite` or `OnlySite(SiteId)`. | `SiteFilter` (sealed) |
| Blog time zone | The zone in which published dates are read as calendar days: `Pacific/Auckland`, so NZST (UTC+12) or NZDT (UTC+13) depending on daylight saving. It is a named constant, not a parameter (owner decision, Q2). | `PublishedDateRange.ZONE` |
| Published-date range | An optional `from` date and an optional `to` date, both inclusive. A post is in range when its `publishedAt`, converted to a calendar date in the blog time zone, falls between them. `from` must not be after `to`. | `PublishedDateRange` |
| Search filters | The site filter and the date range composed into one predicate over post metadata. | `SearchFilters` |
| Result limit | The number of posts to return, clamped to 1..20. It defaults to 10 when absent. | `ResultLimit` |
| Chunk hit | Retired by phase 3: generalised to **entry hit**. | (none) |
| Entry hit | (phase 3) The similarity of one index entry to the query, with the entry's snippet text and what matched. | `EntryHit` |
| Post match | One post in the results: its post metadata, its **best entry**'s similarity as the post's score, that entry's **snippet**, and (phase 3) the **matched image** when the best entry is an image. | `PostMatch` |
| Best chunk | Retired by phase 3: generalised to **best entry**. | (none) |
| Best entry | (phase 3) The entry hit of a post with the highest similarity, over its chunks and its images. If two are equal, a chunk beats an image, then the lower index wins. | `IndexedPost.bestMatch(Embedding)`, `EntryHit.BEST_FIRST` |
| Matched image | (phase 3) The URL of the indexed image that was a post's best entry. Absent when the best entry is a chunk. | `PostMatch.matchedImage()` returning `Optional<URI>` |
| Snippet | The first 60 words of the best entry: a chunk's text without the title line, or (phase 3) an indexed image's description. If the chunk has more words, a trailing ` …` is added and the snippet is marked truncated. It may be empty for a post with an empty body. Trimming was deferred from slice 2 (Q5) and is decided here, in search, rather than in the MCP presenter (slice 3, `docs/domain/app.md` 3.6; proposed, pending owner approval). | `Snippet` (`search.domain.index`), `PostMatch.snippet()` |
| Search results | The post matches ordered by **ranking** and cut to the limit. | `SearchResults` |
| Ranking | Order by score, highest first. Ties go to the more recent `publishedAt` first, and then to the lower post ID (compared as a string). | `PostMatch.RANKING` |
| Search posts | Answer a search query. | `SearchPosts` use case |

### 2.1 Gemma2 image preprocessing (provider-internal, ADR 0011 phase 1)

These terms live only in `providers/gemma2`
(`nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx`). They are a Java port of transformers'
`image_processing_pil_gemma4.py`, and core never sees them. Each step is its own type so it can be
tested alone; `Gemma2ImagePreprocessor` only composes them.

| Term | Meaning | Code name |
|---|---|---|
| Image preprocessing | Turn image pixels into the vision encoder's input: target size, then PIL bicubic resize, then patchify (which rescales). Pure Java, no model. | `Gemma2ImagePreprocessor` |
| Patch | A 16 × 16 pixel square of the resized image. Flattened it is 768 values, ordered by pixel row, then pixel column, then channel R, G, B. | `Patchifier` |
| Patch budget | At most 2,520 patches per image (280 image tokens × 9). It is also the fixed length of the vision encoder input. | `Patchifier.MAX_PATCHES` |
| Target size | The size the image is resized to. Both sides are scaled by one factor so that the area fits the patch budget (`2,520 × 16 × 16` pixels), then each side is floored to a multiple of 48 (16 px × the 3 × 3 pooling). A side that floors to 0 follows the reference processor's fallback (AC-SRCH-57). Always positive multiples of 48, with at most 2,520 patches. | `TargetSize.of(width, height)` |
| Patch grid | The target size counted in patches: **columns** (`width / 16`) and **rows** (`height / 16`), both multiples of 3. Its real patches are `columns × rows`, and its image tokens are `(columns / 3) × (rows / 3)`. The reference JSON records it as `patchGrid: [columns, rows]`. | `PatchGrid` (from `TargetSize.grid()`) |
| PIL bicubic resize | A port of PIL's antialiased bicubic resampling (`Image.resize(…, BICUBIC)` on 8-bit RGB): a horizontal pass then a vertical pass, cubic kernel with `a = −0.5` and support 2, with the support widened by the scale factor when downscaling, fixed-point weights, and results rounded and clamped to 0..255. It is not `AffineTransformOp`, which is a different filter. | `PilBicubicResize.resize(ImagePixels, TargetSize)` returning `ResizedImage` |
| Resized image | Image pixels whose width and height equal a target size. The patchifier only accepts this type, so it never re-checks the size. | `ResizedImage` |
| Rescale | Each 8-bit sample divided by 255, giving 0.0..1.0. There is **no** mean or standard-deviation normalisation. | `Patchifier` |
| Patchify | Cut a resized image into patches in row-major order (left to right, then top to bottom), rescale them, give each patch the position id `(x, y)` = (column, row), then pad to the patch budget: padding patches are all 0.0, with position `(−1, −1)`. | `Patchifier.patchify(ResizedImage)` returning `VisionInput` |
| Vision input | What the vision encoder takes: `pixel_values [1, 2520, 768]`, `pixel_position_ids [1, 2520, 2]` and the patch grid. | `VisionInput` |
| Image input ids | The text-model input ids for one image with `n` image tokens: `[2, 255999, 258880 × n, 258882, 1]` (begin, start of image, `n` image soft tokens, end of image, end). There is no task prefix. | `ImageInputIds.of(PatchGrid)` |
| Vision encoder | The q8 `vision_encoder_quantized` ONNX graph from `onnx-community/embeddinggemma-2-ONNX@daa72c51`, in its own session next to the text model. It outputs `image_features [n, 512]`, which the text model takes through its `image_features` input in place of the empty tensor text calls pass. `n` must equal the patch grid's image tokens. | `Gemma2VisionEncoder` (session), `Gemma2OnnxModel` (text model) |

## 3. Aggregates and value objects

### 3.1 `IndexedPost` (aggregate root, one per post ID)

State: `PostId id`, `PostMetadata metadata`, `ContentFingerprint fingerprint`, `List<IndexedChunk> chunks`,
and (phase 3) `List<IndexedImage> images` (empty for text-only image indexing; see 3.7).

Behaviour:
- **Single decision entry point**: `IndexDecision.forPost(Optional<IndexedPost> existing, PostToIndex post, ContentFingerprint current)`
  delegates to `post.completeness().decide(existing, post, current)`. Both `IndexPost` (events) and
  `ReconcilePlan` (reconciles) call it, and nothing else creates `Add`, `ReEmbed`, `RefreshMetadata`,
  `Keep` or `Exclude`.
  - **`Completeness` owns indexability**, by polymorphism on the enum constant:
    - `SUMMARY.decide(...)` always returns `Exclude(postId)`, whatever exists and whatever the fingerprint.
    - `FULL.decide(...)` returns `existing.map(e -> e.decideFor(post, current)).orElseGet(() -> IndexDecision.forAbsent(post))`,
      with no `if`.
  - `IndexDecision.Exclude.apply` removes any entry, giving `REMOVED`, or `EXCLUDED` when there was none.
    It never calls the embedder.
- `IndexDecision decideFor(PostToIndex post, ContentFingerprint current)`. This **owns the re-index
  decision** for a `FULL` post that is already indexed. It returns `ReEmbed` if `current` differs from the
  stored fingerprint. Otherwise it returns `RefreshMetadata` if the metadata differs, and `Keep` if
  nothing differs. `IndexDecision.forAbsent(post)` returns `Add`.
- `IndexedPost withMetadata(PostMetadata m)`. This returns a copy with the same chunks and
  fingerprint, so it never needs to embed.
- `Optional<PostMatch> bestMatch(Embedding query)`. This **owns snippet choice**. It scores every
  index entry (chunks and, from phase 3, images), picks the best entry with `EntryHit.BEST_FIRST`
  (similarity descending, then chunks before images, then the lower index), and returns empty only
  when the post has no chunks and no images. An image's snippet is its description, and the match
  carries its URL as the matched image.

Created only by the domain service `PostIndexer.index(PostToIndex)`. This service builds the
`WordSequence`, applies the `ChunkingPolicy`, composes the passages, calls `PassageEmbedder.embedPassages`
once for all of the post's passages, and then constructs the aggregate. The embedder is called
**before** anything in the index changes ("embed before mutate").

Invariants:
1. **Identity**: the post ID is fixed. The site ID in the metadata equals the site part of the post ID.
2. **Chunks**: the chunk indexes are exactly `0..n-1`, in body order. A post has zero chunks
   **only** when both its normalised title and its normalised body are empty. Such a post is still recorded,
   so that reconciles do not keep re-adding it, but it never matches a query.
3. **Embeddings**: one per chunk, each with the selected provider's configured dimension and unit
   length, all made under the same index
   recipe as the fingerprint. The composition root selects one embedding provider profile and wires
   its separate passage and query implementations through `PassageEmbedder` and `QueryEmbedder`;
   therefore search queries are embedded in the vector space used by the index.
4. **Fingerprint integrity**: the fingerprint is the one computed from the content the chunks were built
   from. `withMetadata` cannot change content, and new content always goes through `PostIndexer`.
5. **Whole replacement**: an entry is saved and replaced as a whole. A search never sees a mixture of
   old and new chunks of one post (a contract of the `VectorIndex` port).
6. **Only full posts**: an indexed post was always built from a `FULL` post to index. A `SUMMARY` post
   never reaches `PostIndexer`, because `Completeness.SUMMARY` turns it into `Exclude` first.
7. **Images** (phase 3): image indexes are distinct, non-negative and strictly ascending (gaps are
   allowed, because skipped images are not recorded); otherwise `InvalidIndexedPost`. Each indexed
   image has an `https` URL, a non-blank description and an embedding in the same space and recipe
   as the chunks. An indexed image exists only for a settled image, and the stored fingerprint
   covers exactly the settled images (3.7). `withMetadata` keeps the images.

### 3.2 `VectorIndex` (domain port; the repository of `IndexedPost`)

`Optional<IndexedPost> find(PostId)`, `void save(IndexedPost)` (atomic replace),
`boolean remove(PostId)` (returns whether something was removed), `Stream<IndexedPost> all()`
(a consistent snapshot per post), `Set<PostId> ids()`.

`InMemoryVectorIndex` (in `search.adapter.out`, production code) keeps a concurrent map keyed by post ID.
**File persistence is out of scope for this slice and belongs to slice 3**, next to the catalog's
file repositories. Until then, a restart starts with an empty index and the startup reconcile rebuilds it.

Search is a brute-force scan: about 1–2k chunks × 384 dimensions for ONNX BGE; the selected
alternative's configured dimension determines its arithmetic cost. No
approximate-nearest-neighbour structure is needed (ADR 0005).

### 3.3 Value objects and the type that owns each decision

| Decision | Owner | Notes |
|---|---|---|
| Parse post ID and site ID; reject site mismatch and unknown completeness | `PostId`, `SiteId`, `Completeness`, `PostToIndex.of(...)` (a static factory taking JDK types, called by both adapters) | It throws `MalformedCatalogPost` for an invalid published-language payload. |
| Text normalisation: whitespace runs (incl. U+00A0, tabs, newlines) separate words, and leading or trailing whitespace is dropped | `WordSequence` | The title uses the same rule. |
| Splitting overlong words into pieces of at most 64 characters | `WordSequence` | Joining the pieces with no separator gives back the original word. In a snippet the pieces appear separated by spaces. |
| Chunk boundaries and overlap | `ChunkingPolicy` | See 3.4. |
| Token cost of a word | `TokenCounter` port supplied by the selected provider profile; tests: deterministic fake | It must count with the tokenizer matching the loaded model revision. The domain does not select a tokenizer. |
| Provider-specific body/title/total token limits | `PassageTokenBudget` (provider-neutral domain policy/configuration) | Selected at composition with the provider. Concrete tokenizer and context-limit configuration stay outside the domain. |
| Canonical passage content from title and chunk | `PassageComposition` | Provider-specific passage preparation is applied by the selected adapter, not by the domain. |
| Whether a post may be indexed at all | `Completeness.decide(...)` (`FULL` delegates to the normal decision; `SUMMARY` always gives `Exclude`) | Owner decision, Q7. It is reached only through `IndexDecision.forPost`, so events and reconciles share it. |
| What changes re-embedding | `ContentFingerprint.of(IndexRecipe, title, WordSequence, List<PostImage>)`; the current one from `PostIndexer.fingerprintOf(post)` | Tags, URL and dates are excluded. Completeness is decided before the fingerprint matters. The image list comes from `ImageIndexing` (3.7): empty under `TextOnly`, so the fingerprint is exactly the text-only one. |
| Recipe identity | `IndexRecipe` | Derived from selected model/revision identity, `ChunkingPolicy` and passage-budget parameters, and passage-preparation version. Query preparation is excluded. |
| Provider-artifact selection and composition | Provider modules (ADR 0010) and classpath discovery in `app` | Each provider module's jar includes exactly one provider artifact. At runtime `ServiceLoader` discovers exactly one provider from the classpath. No runtime provider selector or default exists; this is not a search-domain aggregate or shared port. |
| Dimension check, finiteness, non-zero norm, normalisation, cosine | `Embedding` | It throws `InvalidEmbedding`. |
| Query validation | `QueryText` | It stores trimmed, validated raw text. Provider-specific query preparation is not a domain rule; the selected adapter applies its configured strategy. |
| Query versus passage preparation | Separate `QueryEmbedder` and `PassageEmbedder` consumer-owned ports; selected adapter's preparation strategies | No boolean flag or provider-specific prefix in the domain. Query preparation is not part of `IndexRecipe`; passage preparation is. |
| Upsert vs remove per event kind | `CatalogEventTranslation` (adapter.in) maps each event type to an `IndexChange` variant; `IndexChange.applyTo(IndexWork)` is polymorphic | Publish and revise **both** become `Upsert`, so duplicate or missed deliveries heal themselves. The pattern switch has one delegating arm per event type. |
| Add / re-embed / refresh / keep / exclude / remove | `IndexDecision` (sealed), produced by the single entry point `IndexDecision.forPost` (for upserts) or `IndexDecision.remove(PostId)` (for withdrawals and orphans). Each variant has `apply(IndexWork)` returning an `IndexOutcome`. | The same type is used by `IndexPost` and `ReconcileIndex`. |
| Reconcile plan (missing, stale, metadata, summary-only, malformed, orphan) | `ReconcilePlan.between(List<CatalogEntry> catalog, VectorIndex snapshot, Function<PostToIndex, ContentFingerprint> currentFingerprint)`; `ReconcileIndex` passes `PostIndexer::fingerprintOf` (phase 3), and the `IndexRecipe` overload fingerprints text only | It produces a list of `IndexDecision`s and an orphan-removal verdict. Each variant contributes through its own method (polymorphism on `CatalogEntry`, not an if-chain):<br>• `Readable` goes through `IndexDecision.forPost`;<br>• `Unreadable` gives `IndexDecision.Unreadable(PostId)`, which never touches the index and reports `FAILED` with the reason. Its ID counts as current;<br>• `Unidentified` contributes no decision, but suppresses all orphan removals in the run.<br>A post ID listed more than once in the catalog becomes **one** `IndexDecision.Unreadable` whose reason mentions "duplicate". Its existing entry stays, it is reported `FAILED`, and the other posts are still planned: a duplicate never aborts the reconcile. A summary-only or malformed catalog post is *not* an orphan. The plan is pure and tested without an embedder. |
| Blank or overlong query | `QueryText` | It throws `InvalidSearchQuery` with reason `BLANK` or `TOO_LONG`. |
| Limit clamping and default | `ResultLimit.of(int)`, `ResultLimit.defaultLimit()` | There are no Optional parameters; the MCP adapter picks the factory. |
| Date-range validity and inclusion | `PublishedDateRange` (factories `unbounded()`, `from(d)`, `to(d)`, `between(a, b)`; `includes(Instant)`; constant `ZONE = Pacific/Auckland`) | It throws `InvalidSearchQuery` with reason `FROM_AFTER_TO`. `includes` converts the instant to a `Pacific/Auckland` calendar date and compares inclusively. The zone is fixed, never passed in, so DST is handled by `java.time` rules. |
| Site inclusion | `SiteFilter` (`AnySite`, `OnlySite`) with `includes(SiteId)` | |
| Composition of filters | `SearchFilters` (a list of `Predicate<PostMetadata>`; all must hold) | It is a rule table, so adding a tag filter later means adding a rule. |
| Best entry and snippet | `IndexedPost.bestMatch` using `EntryHit.BEST_FIRST` (similarity descending, then chunks before images, then index ascending) | Phase 3 generalises `ChunkHit`. Each `IndexEntry` builds its own hit (`hitFor(query)`), so `bestMatch` never asks which kind it has. |
| Grouping, ranking and tie-breaks, then limit | `SearchResults.rank(Stream<PostMatch>, ResultLimit)` with `PostMatch.RANKING` | Grouping is structural: there is one `bestMatch` per indexed post. |
| Valid image raster (dimensions, pixel cap, data length, immutability) | `ImagePixels` compact constructor, a rule table of three rules checked in order | It throws `InvalidImagePixels` with a reason. See 3.6. |
| Whether a provider can embed images | Each `EmbeddingProvider` implementation's `imageEmbedder()` (abstract in the SPI, no default) | Callers use `Optional.map`, never a provider-name check. |
| Target size, patch grid and image token count (provider-internal) | `TargetSize.of(width, height)` and `PatchGrid` | Pure arithmetic; tested without the model (AC-SRCH-56, 57). |
| Resampling (provider-internal) | `PilBicubicResize` | AC-SRCH-58; PIL parity checked within tolerance by AC-SRCH-61, and by cosine in AC-SRCH-62. |
| Resize intermediate within 48 MiB (provider-internal) | `IntermediateSize` compact constructor (target width × source height × 3, in `long`) | It throws `ImageTooLargeToResize`, which `Gemma2OnnxEmbedder` reports as `EmbedderUnavailable` (AC-SRCH-65). |
| Patch order, rescale, position ids and padding (provider-internal) | `Patchifier`, which accepts only a `ResizedImage` | AC-SRCH-59. |
| Image input ids (provider-internal) | `ImageInputIds.of(PatchGrid)` | AC-SRCH-60. |
| Vision output matches the image token count (provider-internal) | `Gemma2VisionEncoder` checks the `image_features` row count against `PatchGrid.imageTokens()` | A mismatch is an `IllegalStateException`, which `Gemma2OnnxEmbedder` turns into `EmbedderUnavailable` (AC-SRCH-65). |

### 3.4 Chunking policy (numbers and why)

Provider-neutral parameters: `TARGET_WORDS = 300`, `OVERLAP_WORDS = 50`,
`MAX_WORD_CHARS = 64`. The selected `PassageTokenBudget` supplies the body-token budget, overlap-token
cap, title-token budget and total prepared-passage budget. The existing ONNX BGE profile retains
`BODY_TOKEN_BUDGET = 400`, `OVERLAP_TOKEN_CAP = 100`, `TITLE_TOKEN_BUDGET = 64` and its 465-token
worst-case passage target below; these numbers are not defaults for another provider.

Algorithm (deterministic):
1. A chunk starts at word `s` and grows one word at a time. It **closes before** a word that would make it
   exceed 300 words or the selected profile's body-token budget.
2. The next chunk starts at the longest suffix of the previous chunk that has at most 50 words **and**
   at most the selected profile's overlap-token cap, but always at least one word after the previous start, so every chunk adds at least one new word.
   The overlap also leaves room in that profile's body-token budget for the next word. If the overlap would leave no room for
   a new word under the selected budget, it shrinks instead of producing a chunk that is only overlap. Each profile must
   budget enough tokens for at least one split word.
3. Chunking stops when the chunk just closed contains the last word. No chunk consists only of overlap.
4. An empty word sequence gives **one** chunk with empty text, so canonical passage content is the title alone.
   If the title is also empty, the result is **zero** chunks.

Why these numbers:
- **300 words** is the size the plan asked for. It is about one topical section of a blog post: big enough to carry
  meaning, and small enough that one strong paragraph is not diluted by a whole post. For ONNX BGE, English prose
  costs about 1.3 WordPiece tokens per word, so about 390 tokens.
- For ONNX BGE, **400 body tokens** is the hard guarantee. Technical posts contain code, identifiers and punctuation that cost
  2–3 tokens per "word", so a word count alone cannot keep a chunk within the model's input.
  LangChain4j does not truncate long input. It splits anything over **510 content tokens** into
  partitions and averages their embeddings weighted by length, which would blur a chunk's meaning. So
  the goal is that **every passage fits in a single partition**. The worst case is 64 (title) + 400 (body) +
  1 (the newline adds no WordPiece token, but is counted conservatively) = **465 content tokens ≤ 510**
  (467 ≤ 512 with `[CLS]`/`[SEP]`). That leaves a margin of 45 tokens for any non-additivity in the tokenizer.
- **50 words of overlap (about 17%)** is two or three sentences. For ONNX BGE the 100-token cap stops a code-heavy overlap
  from eating the next chunk's budget. Each profile's cap must leave room for at least one new (split) word, so chunking terminates.
- **64-character pieces**: each selected profile's budgets are enforced by exact counting with its configured tokenizer,
  not by a characters-per-token bound. A "one token per character" bound would not
  hold anyway: the tokenizer's NFD normalisation can expand one Hangul syllable into 2–3 jamo. The
  64-character pieces keep any single word within the configured body budget, so a single word can
  never fill a chunk on its own. AC-SRCH-8's input includes a Hangul fragment for this reason.
- Expected volume: 229 posts, mostly 300–1,500 words, gives roughly 700–1,500 chunks.

How "one partition" is ensured:
- For ONNX BGE, LangChain4j keeps its token methods package-private, so `BgeTokenCounter` loads the same
  `bge-small-en-v1.5-q-tokenizer.json` that the model uses, through DJL's `HuggingFaceTokenizer`. DJL is
  already a transitive dependency, so this is the exact tokenizer and not an estimate.
- BERT's pre-tokenizer splits on whitespace and punctuation before WordPiece, so the cost of a text is
  the sum of the costs of its words (additive). The budget is checked word by word while chunking.
- AC-SRCH-8 checks the result against the real tokenizer.

Queries: query validation and provider-specific query preparation are separate. The query preparation
strategy and any provider-specific query budget are selected with the provider; a configured OpenAI-
compatible endpoint does not supply either. Query limits do not determine passage chunk limits.

### 3.5 Ranking choice

A post's score is its **best chunk's** similarity (max-pooling).
- A sum or count of chunk hits would favour long posts.
- A mean would punish a long post with one highly relevant section.

The best chunk is also the natural snippet, so the score and the snippet always agree. Ties are rare
with floats but must be deterministic: newer `publishedAt` first, then post ID ascending.
No minimum-similarity threshold is applied (Q4). BGE v1.5 similarities cluster in about [0.6, 1].
The model card warns that a score above 0.5 does not mean "similar" and recommends relying on relative
order rather than an absolute cut-off, which is exactly what ranking does.

### 3.6 Image embedding (ADR 0011, phase 1)

Phase 1 adds no aggregate. It adds a value object, a port and a provider capability, so that phase 3
can index images and phase 4 can search by one.

**`ImagePixels`** (value object, `record ImagePixels(int width, int height, byte[] rgb)` in
`search.domain.embedding`). It lives in the domain because phase 3's `PostIndexer` will pass it on.
Invariants, checked in this order by a rule table, each producing at most one `InvalidImagePixels`:
1. **Positive dimensions**: `width ≥ 1` and `height ≥ 1`, otherwise `NON_POSITIVE_DIMENSION`.
2. **Pixel cap**: `width × height ≤ 16,777,216` (4,096 × 4,096), computed in `long` so it cannot
   overflow, otherwise `TOO_MANY_PIXELS`. This is checked before the data length, so an oversized
   request is refused without needing its bytes. The cap bounds memory: at most 48 MiB of samples. It
   is above a 12-megapixel phone photo (4,032 × 3,024) and far above the catalog's 2,048 px `srcset`
   limit (cap confirmed by the owner, 2026-10-13).
   The gemma2 provider keeps its resize within the same bound: the image between its horizontal and
   vertical passes (target width × source height × 3 samples, computed in `long`) may hold at most
   48 MiB. Only a very tall, narrow image exceeds it, e.g. 1 × 16,777,216, whose 1-pixel width is
   upscaled to 48. Such an image is valid `ImagePixels`, but the provider refuses it before
   allocating, as `EmbedderUnavailable` (AC-SRCH-65).
3. **Data length**: `rgb.length == width × height × 3`, otherwise `DATA_LENGTH_MISMATCH`. A `null`
   array is rejected with `NullPointerException`.
4. **Immutability**: the compact constructor stores a copy of `rgb`, and the `rgb()` accessor returns a
   copy. `equals` and `hashCode` compare dimensions and contents (a record's default compares array
   references), and `toString` shows only the dimensions.

Decoding (JPEG, PNG, GIF to `ImagePixels`) is **not** in phase 1. Phase 3's `HttpImageSource` and
phase 4's local-file reader own it. They must match PIL's `convert("RGB")`, which drops alpha
without compositing, expands palettes and replicates greyscale. They should also read the declared
dimensions before decoding, so the pixel cap stops a decompression bomb early. Phase 1's parity
tests decode the six fixtures in test code only.

**`ImageEmbedder`** (port, `search.domain.embedding`, next to `PassageEmbedder`, because phase 3's
`PostIndexer`, a domain service, calls it; ADR 0006): `Embedding embedImage(ImagePixels)`. The embedding
has the provider's dimension and unit length, like every `Embedding`, and lies in the same space as
`QueryEmbedder` and `PassageEmbedder` output. Failure to load or run the model raises
`EmbedderUnavailable`.

**`EmbeddingProvider.imageEmbedder()`** returns `Optional<ImageEmbedder>`. It is abstract, so every
provider states its answer: `onnx` and `gemma2-server` return `Optional.empty()`, and `gemma2`
returns its embedder. Calling it never loads a model, because phase 4 asks at startup whether to
register `find_posts_like_image`.

**In-process `gemma2`** (provider module): `embedImage` runs `Gemma2ImagePreprocessor` (glossary 2.1),
then the vision encoder, then `Gemma2OnnxModel` with the image input ids and the real
`image_features`, all under the existing model lock (so image and text calls are serialised, with no
new concurrency work). The vision encoder is loaded lazily in its own holder, so a process that only
embeds text never extracts or loads its 195 MB. Its files are extracted by `BundledModelFiles`, the
same way as the text model. Text calls are unchanged: they still pass empty media tensors.

### 3.7 Indexing post images (ADR 0011, phase 3; approved and implemented)

Each image is its own index entry beside the post's text chunks, embedded from its pixels alone, and
a post scores as its best entry (fixed by ADR 0011). This section decides how that fits the existing
decide-then-apply and reconcile model without a new index decision.

**Flow.** `PostIndexer.index(post)` is unchanged for text: it chunks, composes and embeds all passages
in one call, and a text failure still fails the whole post before anything changes (AC-SRCH-17).
It then hands the post's images to its `ImageIndexing` strategy:
- `TextOnly` returns no images and fetches nothing.
- `WithImages` takes each post image in catalog order, calls `ImageSource.fetch(imageUrl, postUrl)`
  with the post's canonical URL, and turns the
  `ImageFetch` into an `ImageAttempt` with a pattern switch whose arms each delegate once:
  - `Fetched(pixels)`: `ImageEmbedder.embedImage(pixels)` gives `Indexed(IndexedImage)`. If the
    embedder throws `EmbedderUnavailable`, the attempt is `Pending`;
  - `Rejected(reason)`: `Skipped`. It is settled, with no entry;
  - `Unavailable(detail)`: `Pending`. It is not settled.

  Images are embedded one at a time (one `embedImage` call each), so one bad image affects only itself.

The aggregate is then built from the chunks, the indexed images, and the fingerprint over the
**settled** images (`Indexed` and `Skipped`), so the entry is still saved whole.

**Retry (the decision for "a failed image is retried on the next reconcile").** No new state, decision
or outcome is added. The current fingerprint (used by `IndexDecision.forPost`) covers **all** of the
post's images, but the stored one covers only the settled ones. A post with a pending image is
therefore stale: the next upsert of that post (an event, or the next reconcile at the latest) gives
`ReEmbed`, which re-embeds the text and tries every image again. Once every image is settled, the two
fingerprints are equal and the post is `Keep` again. Rejected images are settled, so a WebP image or a
tracking pixel is fetched once per content version, never on every reconcile. The cost of this
simplicity is that a retry re-embeds the post's text too (accepted by the owner, Q-IMG 7).

**Text-only providers are untouched.** `TextOnly.appliedTo(recipe)` returns the recipe unchanged and
`TextOnly` fingerprints with an empty image list, which yields exactly today's fingerprint. So the
`onnx` and `gemma2-server` indexes stay valid, no image is ever fetched, and an images-only revision
(including those that follow the catalog's format-1 upgrade, catalog Q9) is `Keep`. `WithImages`
appends `/images-v1` to the recipe, so the `gemma2` index is re-embedded once (ADR 0011
Consequences).

**Composition.** `app` chooses the strategy once:
`provider.imageEmbedder().map(e -> ImageIndexing.withImages(new HttpImageSource(errors), e)).orElseGet(ImageIndexing::textOnly)`.
`HttpImageSource` needs no site configuration: there is no host allow-list, and the address scope
comes from the post's own canonical URL (owner decision, 2026-10-14). For each fetch it resolves the
post host once to get the **blog category**, then checks the image host and every redirect target
against it. A text-only distribution never constructs an `HttpImageSource`.

**Port placement (owner decision, Q-IMG 5).** The plan first put `ImageSource` in
`search.application`. But `PostIndexer`, a domain service, calls it (through `ImageIndexing`), so ADR
0006 places it in the domain, for the same reason `ImageEmbedder` is there (Q-IMG 3). It lives in a
new sub-package `search.domain.image` with `PostImage`, `ImageFetch` and `ImageRejection`. That
sub-package depends on `embedding` (for `ImagePixels`), `post` (for `MalformedCatalogPost`) and
`text` (for whitespace normalisation of the description), none of which depends back on it, and
`index` depends on it, so the graph stays acyclic (6.1).

| Decision | Owner | Notes |
|---|---|---|
| What a valid post image is | `PostImage.of(String url, String altText, String caption)` (`search.domain.image`) | A null field, or a URL that is not absolute `https` with a host, raises `MalformedCatalogPost`. The post becomes `Unreadable` in a reconcile, or is rejected by the listener, like any other malformed field. Both anti-corruption adapters (`SharedCatalogPosts` and `CatalogEventTranslation`) map images, so events and reconciles compute the same fingerprint. |
| What stands for an image in results | `PostImage.description()` | `Optional` chain over alt text, then caption, then `Image: <url>`; no `if`. |
| Text-only versus image indexing | `ImageIndexing` (`TextOnly`, `WithImages`), chosen once in `app` | Owns `appliedTo(IndexRecipe)`, the fingerprint's image list (`fingerprinted(post)`) and one `ImageAttempt` per post image (`attempt(post)`), which `PostIndexer.index(post)` turns into indexed images and the settled-image fingerprint. |
| What a fetch outcome means for the index | `ImageFetch` variant, mapped to `ImageAttempt` (`Indexed`, `Skipped`, `Pending`) | `ImageAttempt` exposes `entry()` and `settled()` polymorphically, so `WithImages` collects them with a stream. |
| Retry of a pending image | `ContentFingerprint` over the settled images, compared by the existing `IndexedPost.decideFor` | No new `IndexDecision` variant or `IndexOutcome`. |
| Image index invariants | `IndexedPost.restore` (invariant 7), `IndexedImage` compact constructor | |
| Best entry, snippet and matched image | `IndexEntry.hitFor(query)` per entry kind, `EntryHit.BEST_FIRST`, `PostMatch.matchedImage()` | |
| Scheme | `HttpImageSource` | Only `https`, on any host and port. Checked before every request, including redirects; otherwise `NOT_HTTPS`. There is no host allow-list (owner decision, 2026-10-14). |
| Which image addresses a blog permits | `AddressCategory` (package-private enum): `AddressCategory.of(InetAddress)` is a rule table, and each category owns `permits(AddressCategory)` | The blog category is the least-permissive category among the post host's resolved addresses. Every resolved address of the image host, and of each redirect target, must be permitted by it; otherwise `Rejected(ADDRESS_NOT_PERMITTED)`, with no request sent to that host. Only a `NON_ROUTABLE` blog permits `NON_ROUTABLE` images. A post host that does not resolve gives `Unavailable`. The categorisation and the permits table are tested on the enum. |
| Host resolution | `HostResolver` (package-private seam in `search.adapter.out`, wrapping `InetAddress`) | An adapter-only abstraction we own (ADR 0006), not a double of a JDK type. The production resolver uses `InetAddress.getAllByName`. Tests use a fixed-answer resolver, so a "public" image host can redirect to an internal one under WireMock. A WireMock post URL on `localhost` is a `LOOPBACK` blog, and a public IP literal (e.g. `https://93.184.216.34/p`) is a `PUBLIC` blog without DNS. |
| Redirects | `HttpImageSource` follows them itself (the client never follows redirects), at most 3 | Each target must be `https` and be permitted by the blog category. The fourth redirect is `TOO_MANY_REDIRECTS`. |
| Response status | `FetchStatus` (package-private) | 2xx: read the body. 3xx with `Location`: redirect. 408, 429, 5xx, timeouts and I/O errors: `Unavailable`. Any other status: `Rejected(HTTP_STATUS)`. |
| Content type | `ImageContentType` (package-private) | `image/jpeg`, `image/png`, `image/gif` (case-insensitive, parameters ignored) are accepted. Anything else, including a missing header and `image/webp`, is `Rejected(UNSUPPORTED_TYPE)`. |
| Size | `HttpImageSource` byte cap: 10 MiB (10,485,760 bytes) | A larger `Content-Length` is refused before reading. A body is read only up to the cap, and one byte more is `TOO_LARGE`. |
| Decoding | `ImageDecoding` (package-private, `ImageIO`) | Reads the declared dimensions first: over 16,777,216 pixels is `TOO_LARGE`, and a side under 64 is `TOO_SMALL`, both without decoding. It decodes the first frame (GIF), drops alpha without compositing, expands palettes and replicates greyscale, as PIL's `convert("RGB")` does (3.6). A decoding failure is `UNDECODABLE`. |
| Timeouts | `HttpImageSource` constants | Connect 10 s; each response 30 s, covering its headers and its whole body (a body that drips past the deadline cancels the exchange). Expiry is `Unavailable`. |
| Logging | `HttpImageSource` | Every outcome other than `Fetched` writes one line to stderr with the URL and the reason (this is the plan's "WebP skipped with a warning"). Nothing is added to the reconcile report. |
| Stored index format | `IndexFile` / `IndexFileLoader` (`search.adapter.out`) | Writes `"format": 2`, which adds `images`: an array of `{index, url, description, vector}` (vector encoded by `VectorCodec`, like chunks). Reads formats 1 and 2: a format-1 file is read as having no images, and is neither quarantined nor deleted, following catalog Q9. Under `TextOnly` its fingerprint is still current, so nothing is re-embedded; under `WithImages` the recipe differs, so the next reconcile re-embeds it anyway. Any other format is still unreadable and quarantined. The dimension rule covers image vectors as well as chunk vectors. |
| MCP output | `SearchResultView` (`app.mcp`) and `ToolDefinitions` | An optional `matchedImage` string (`format: uri`) in each result, not in `required`. It is present only for an image match, and the key is omitted (not `null`) otherwise. |

## 4. Domain events

### 4.1 Consumed (published language in `nz.sounie.blogmcp.shared.event`)

| Integration event | Translated to | Effect |
|---|---|---|
| `CatalogPostPublished` | `IndexChange.Upsert(PostToIndex)` | A `SUMMARY` post is `Exclude`, so nothing is indexed. A `FULL` post is `Add` if absent. If already present (duplicate delivery, or the reconcile got there first), the fingerprint decides, usually `Keep`. |
| `CatalogPostRevised` | `IndexChange.Upsert(PostToIndex)` | The completeness is checked first. A revision to `SUMMARY` is `Exclude`, which removes any entry. A revision from `SUMMARY` to `FULL` finds no entry, so it is `Add`. For a `FULL` post that is already indexed, the fingerprint decides between `ReEmbed`, `RefreshMetadata` and `Keep`. The event's `changed` set is **not** used for the decision. The fingerprint is authoritative and also works for reconciles, which have no `changed` set. A tags-only, URL-only or published-at-only revision therefore causes **no re-embedding**. An images-only revision (`changed = {"IMAGES"}`) re-embeds only under image indexing, and is `Keep` for a text-only provider (3.7). A revision for a `FULL` post that is not indexed (a missed publish) is `Add`. |
| `CatalogPostWithdrawn` | `IndexChange.Remove(PostId)` | `REMOVED`, or `ALREADY_ABSENT` (idempotent). The reason is ignored. |

`CatalogEventListener` (in `search.adapter.in`) subscribes to the in-process bus. The bus is
synchronous, so indexing runs on the catalog's sync thread. The listener **isolates failures**: any
exception from translation or indexing is logged to stderr (stdout is reserved for the MCP stdio
protocol in slice 3) and swallowed. That way a search failure can never abort a catalog sync, and the next reconcile
repairs the entry.

Index mutations (`IndexPost` and `ReconcileIndex`) are serialised by a single lock in search's
application layer, so that a decision and its save cannot interleave with another mutation of the same post.
Searches do not take that lock.

**Embedding providers and model access.** ONNX BGE, in-process Gemma2 and LM Studio Gemma2 are
separate provider modules (ADR 0010); each provides one instance implementing both consumer-owned
ports. Each module builds its own distribution jar containing exactly that provider. The app discovers that provider from the runtime
classpath; it reads no provider-selection environment variable. The provider supplies its own
query/passage strategies, matching tokenizer and explicit passage-token budget. Model-specific
instructions, tokenizers and context facts remain in adapters/configuration, not the domain. Passage
and query vectors must use the same configured model/revision. Model identity, passage preparation
and budget contribute to `IndexRecipe`; changing an indexing-relevant value makes entries stale
within that profile's index (AC-SRCH-40–50).

For ONNX BGE, LangChain4j shares one ONNX session and one tokenizer per model, and its own
`embedAll` already calls them from a thread pool sized to `availableProcessors()`. That suggests
concurrent use is intended, but nothing documents it. Because searches do not take the index lock, a
query embedding can run at the same time as an indexing embedding. **Recommendation (adopted in ADR
0005):** `OnnxEmbedder` serialises its own public calls (`embedPassages` for one post,
`embedQuery`) with one lock, rather than relying on that undocumented assumption.
- The cost is bounded. One post's passages take about 0.1–0.5 s (an estimate). A query waits at most
  that long, even during a reconcile, because reconciles call the embedder once per post.
- LangChain4j's parallelism *within* one `embedAll` call is left alone, because that is its own
  exercised path.

### 4.2 Published

None. Provider selection is resolved once during application startup, not published as a domain
event. No other context needs to know about indexing. Outcomes are returned as results
(`IndexOutcome`, `ReconcileReport`, `SearchResults`) rather than raised as events.

## 5. Reconcile and rebuild (resolves catalog.md Q8; see ADR 0005)

- **Contract**: `nz.sounie.blogmcp.shared.query.CatalogPosts` with `List<CatalogPostState> currentPosts()`.
  `CatalogPostState` is a record of JDK types with the same fields as `CatalogPostPublished`:
  postId, siteId, canonicalUrl, title, body, **completeness** (`FULL` or `SUMMARY`), tags, publishedAt and updatedAt. It is sorted by post ID,
  and each post ID appears once. It lists **every** stored post, including summary-only ones; search
  decides what to exclude. The catalog implements it in `catalog.application` (`ListCatalogPosts`, over
  `PostRepository`), in this slice (owner decision, Q1).
- **Contract check (completeness)**: the existing `CatalogPostPublished` and `CatalogPostRevised` already carry
  `completeness` as a `String` (`FULL` or `SUMMARY`), so no event change is needed. `CatalogPostWithdrawn`
  does not need it. `CatalogPostState` is new and includes it, as part of AC-SRCH-31.
- **Contract change (post images, ADR 0011 phase 2, proposed in `catalog.md` 3.6 and section 4):**
  `CatalogPostPublished`, `CatalogPostRevised` and `CatalogPostState` gain
  `List<shared.query.CatalogPostImage> images`, and `changed` may contain `"IMAGES"`. Search ignores both
  until phase 3. `SharedCatalogPosts` and `CatalogEventTranslation` do not read `images`, and the
  fingerprint does not include them. So an images-only revision, including the ones that follow the
  catalog's format-1 upgrade (catalog Q9), is `Keep` and re-embeds nothing.
  **From phase 3 (proposed):** both adapters map `images` into `PostToIndex` through `PostImage.of`
  (AC-SRCH-66). Whether they reach the fingerprint depends on the image-indexing strategy (3.7):
  never for a text-only provider, so the paragraph above still holds there.
- **Reading the contract**: search reads it only through `SharedCatalogPosts` (`search.adapter.out`), which
  implements search's own port `PostCatalog`. It translates **every** state into a `CatalogEntry` and
  **never drops one**:
  - `Readable(PostToIndex.of(...))` when the state is valid;
  - `Unreadable(PostId, reason)` when the post ID parses but another field is malformed or null;
  - `Unidentified(reason)` when the post ID cannot be read.
- **Staleness detection**: search does not trust timestamps or `changed` sets. For each catalog post, it computes
  the content fingerprint under the **current** index recipe and compares it with the stored one. It also compares
  the post metadata by equality. Post IDs present in the index but absent from the catalog (as readable or
  unreadable entries) are orphans.
- **Plan**: `ReconcilePlan.between(...)` gives one `IndexDecision` per catalog entry with a known ID:
  - a readable entry goes through `IndexDecision.forPost`, so summary-only posts become `Exclude`;
  - an unreadable entry becomes `IndexDecision.Unreadable`, which leaves any existing entry untouched and
    is reported as `FAILED` with its reason.

  It plans a `Remove` per orphan **only if no catalog entry is unidentified**. Otherwise no orphan is
  removed in that run, and the report carries `ORPHAN_REMOVAL_SUPPRESSED` (the same precaution as catalog AC-CAT-23). `ReconcileIndex` applies them **one post at a time** (one aggregate per transaction). A
  failure (for example, the embedder is unavailable) is recorded as `FAILED` for that post, and the remaining posts still go ahead.
- **When it runs** (wired in slice 3): once at startup (with the in-memory index, this is the full
  rebuild), and after each scheduled catalog `SyncAllSites` has finished, sequentially in the same scheduler job.
  So a reconcile never reads a catalog snapshot while that catalog is being synced.
- **Empty catalog**: a reconcile against an empty catalog removes every indexed post. The catalog is local
  and authoritative, unlike a remote listing, so there is no suppression (owner decision, Q8).

## 6. Context map

- **catalog to search (customer/supplier, published language)**: catalog is the supplier through two
  contracts in `shared`: the integration events (push) and the `CatalogPosts` query (pull, for
  reconciles). Search translates both at its boundary (anti-corruption layer: `CatalogEventTranslation`
  and `SharedCatalogPosts`) into `PostToIndex`. The pull side wraps each post in a `CatalogEntry`, so
  malformed states stay visible. Neither `search.domain` nor `search.application` sees
  `shared` types.
- **search to embedding providers (conformist, wrapped)**: each provider is a separate module whose
  distribution jar contains exactly that provider (ADR 0010), and `app` discovers it through the SPI
  at the `search.application` boundary. The same
  discovered provider instance implements the separate consumer-owned embedding ports. Neither SPI
  nor provider depends on `app`. Provider tokenizers and model-specific preparation remain outside
  the domain; the selected tokenizer implements the separate `TokenCounter` port. Missing or
  ambiguous classpath discovery fails startup.
  The ONNX model and tokenizer files are bundled in the LangChain4j jar, so nothing is downloaded
  at runtime. The selected provider loads lazily; Gemma2's built-in pinned profile and usable
  tokenizer assets from the matching revision are supplied by its separate provider artifact.
- **search to mcp (slice 3)**: the `search_posts` tool calls `SearchPosts` and maps its arguments with
  `SearchQuery`'s factories (`ResultLimit.defaultLimit()` when `limit` is absent,
  `PublishedDateRange.unbounded()` when both dates are absent, and so on).

### 6.1 Domain sub-packages (ADR 0006)

`search.domain` is split by concept into sub-packages with acyclic dependencies. ArchUnit's
`domain_sub_packages_are_free_of_cycles` enforces this.

| Sub-package | Depends on | Types |
|---|---|---|
| `post` | (nothing) | `PostId`, `SiteId`, `PostMetadata`, `MalformedCatalogPost` |
| `text` | (nothing) | `WordSequence`, `WordCosts`, `ChunkingPolicy`, `PassageTokenBudget`, `Chunk`, `Passage`, `PassageComposition`, `TokenCounter` (port) |
| `embedding` | `text` | `Embedding`, `Similarity`, `InvalidEmbedding`, `EmbedderUnavailable`, `PassageEmbedder` (port), `ImageEmbedder` (port), `ImagePixels`, `InvalidImagePixels` |
| `image` (phase 3) | `embedding`, `post`, `text` | `PostImage`, `ImageSource` (port), `ImageFetch`, `ImageRejection` |
| `index` | `embedding`, `image`, `post`, `text` | `IndexedPost`, `IndexedChunk`, `IndexedImage` and `IndexEntry` (phase 3), `ImageIndexing` and `ImageAttempt` (phase 3), `PostIndexer`, `PostToIndex`, `Completeness`, `PostMatch`, `EntryHit` (was `ChunkHit`), `ContentFingerprint`, `IndexRecipe`, `IndexChange`, `IndexDecision`, `IndexOutcome`, `IndexWork`, `InvalidIndexedPost`, `VectorIndex` (port) |
| `query` | `index`, `post`, `text` | `SearchQuery`, `QueryText`, `InvalidSearchQuery`, `ResultLimit`, `PublishedDateRange`, `SiteFilter`, `SearchFilters`, `SearchResults` |
| `reconcile` | `index`, `post` | `CatalogEntry`, `ReconcilePlan` |

Nothing in the domain depends on `query`. `PostToIndex`, `Completeness` and `PostMatch` live in `index`,
not in `post` or `query`, to keep this graph acyclic.
Outside the domain:
- `PostCatalog` and `QueryEmbedder` (ports used only by use cases), `ReconcileReport` and the use cases live in `search.application`. `ImageEmbedder` lives in `search.domain.embedding`, next to `PassageEmbedder`, because phase 3's `PostIndexer` (a domain service) calls it (ADR 0006; owner decision 2026-10-13);
- provider adapters, their preparation strategies/tokenizers, `InMemoryVectorIndex`, `SharedCatalogPosts` and (phase 3) `HttpImageSource` with its package-private helpers live in `search.adapter.out`;
- `CatalogEventListener` and `CatalogEventTranslation` live in `search.adapter.in`.

## 7. Application use cases (`search.application`)

- `IndexPost.apply(IndexChange)` returns an `IndexOutcome`. It reads as a straight line: decide, then apply the decision.
- `SearchPosts.search(SearchQuery)` returns `SearchResults`: `embedQuery(query.text())` (the selected adapter prepares it), then `index.all()`, filtered
  by `SearchFilters`, then `bestMatch` for each post, then `SearchResults.rank(limit)`.
  A `PostMatch` carries the post ID, site ID, canonical URL, title, tags, published at, updated at, score,
  snippet and (phase 3) the optional matched image. It has **no completeness**, because every indexed post is `FULL`.
- `ReconcileIndex.run()` returns a `ReconcileReport`, with per-post outcomes, failure reasons and the
  orphan-removal-suppressed flag. It reads as a straight line: read the entries, plan, then apply each decision.
- Port `PostCatalog`: `List<CatalogEntry> currentPosts()`. A malformed state becomes an `Unreadable` or
  `Unidentified` entry, never a silent omission.

## 8. Acceptance criteria

Unless an AC says otherwise, chunking ACs use a fake `TokenCounter` that costs **1 token per word**, and
index and search ACs use a deterministic fake embedder (`FakeEmbedder` in `src/test`, implementing both `PassageEmbedder` and `QueryEmbedder`) whose vectors are
chosen by the test. Posts are `FULL` unless an AC says `SUMMARY`.

### Chunking and passages

**AC-SRCH-1: A short post is one chunk.**
Given a post whose body has 120 words (or exactly 300 words),
When it is chunked,
Then there is exactly one chunk, with index 0, containing every word in order.

**AC-SRCH-2: A long post is several overlapping chunks.**
Given a post whose body has 700 words `w1..w700`,
When it is chunked,
Then there are 3 chunks covering `w1..w300`, `w251..w550` and `w501..w700`, and consecutive chunks share exactly 50 words.
And given a body of 301 words, there are 2 chunks covering `w1..w300` and `w251..w301`.

**AC-SRCH-3: The selected passage-token budget closes a chunk early, and the overlap is token-capped.**
Given the selected test profile declares a 400-token body budget and a 100-token overlap cap, and a fake `TokenCounter` that costs 2 tokens per word with a body of 500 words,
When it is chunked,
Then the first chunk is `w1..w200` (400 tokens), and the next one starts at `w151` (50 words = 100 tokens).
And given a counter costing 4 tokens per word, the overlap is 25 words (the 100-token cap), not 50.
And with any selected profile and counter, no chunk exceeds that profile's body-token budget, and every chunk contains at least one word not in the previous chunk. These 400/100 limits are the ONNX BGE profile values, not universal defaults.

**AC-SRCH-4: An empty body is indexed by its title alone. An empty post has no chunks.**
Given a post titled `Hello` whose body is empty (or only whitespace),
When it is indexed,
Then it has one chunk with empty text, whose passage is exactly `Hello`, and a query matching the title can find it.
And given a post whose title and body are both blank, it is recorded in the index with zero chunks, and it never appears in search results.

**AC-SRCH-5: Overlong words are split, so they cannot overflow a chunk.**
Given a body containing a single 1,000-character word with no whitespace (e.g. base64 data) between ordinary words,
When it is chunked,
Then that word becomes 16 pieces (15 of 64 characters and 1 of 40), joining them with no separator reproduces the original, and no chunk exceeds the token budget.

**AC-SRCH-6: Text normalisation, and fingerprints ignore whitespace-only edits.**
Given a body `"a\tb\n\nc d   e"`,
When it is normalised,
Then the word sequence is `a b c d e`.
And given two versions of a post that differ only in whitespace, their content fingerprints are equal.

**AC-SRCH-7: The title is part of every passage and obeys the selected profile's title budget.**
Given a post titled `Records in Java 25` with a 700-word body,
When its passages are composed,
Then each of the 3 passages is `Records in Java 25`, a newline, and then that chunk's text.
And given a title longer than the selected profile's title-token budget, the canonical title content is its longest whole-word prefix within that budget (64 tokens for the ONNX BGE profile).
And given a blank title, the passage is the chunk text alone.

**AC-SRCH-8: Every ONNX BGE passage fits in one model partition (adapter test, real tokenizer).**
Given a pathological body of 3,000 "words" mixing Java code, long identifiers, punctuation runs, URLs, a 2,000-character string with no whitespace, and a run of Hangul syllables with no whitespace (which NFD normalisation expands into jamo, so it costs more than one token per character), and a 120-word title,
When it is chunked and composed under the ONNX BGE profile with the real `BgeTokenCounter` as `TokenCounter`,
Then every passage, encoded as a whole by the model's tokenizer (`bge-small-en-v1.5-q-tokenizer.json` through DJL), has at most 465 content tokens. That is within LangChain4j's single-partition limit of 510, so no passage is split and averaged.
(The test JVM must point DJL's cache at a writable directory; see ADR 0005.)

### Embedding

**AC-SRCH-9: The `Embedding` value object.**
Given a selected provider dimension, a vector whose length does not equal that dimension, one containing `NaN` or infinity, or the all-zero vector,
When an `Embedding` is created for that dimension,
Then `InvalidEmbedding` is raised.
And given a valid vector of the selected dimension with length 3.0, the embedding has unit length (to within 1e-6), its similarity with itself is 1.0, and its similarity with an orthogonal embedding of the same dimension is 0.0. Provider dimensions come from confirmed model configuration, not from the local server alias.

**AC-SRCH-10: The real model ranks related text above unrelated text (one adapter test).**
Given the real `OnnxEmbedder`, the query `how do I configure a Gradle build for Java`, a related passage about writing a `build.gradle.kts` for a Java project, and an unrelated passage about baking sourdough bread,
When the raw query is passed to the ONNX BGE query-preparation strategy and both passages are prepared and embedded by the ONNX BGE passage strategy,
Then all embeddings have dimension 384 and unit length, and the related passage's similarity to the query is strictly greater than the unrelated one's. Only the relative order is asserted, never an absolute score (BGE scores cluster in about [0.6, 1]).

**AC-SRCH-32: Provider-specific preparation is separate from query validation and passage composition.**
Given the raw query text `records in java`,
When a `SearchQuery` is searched,
Then the `QueryEmbedder` receives the validated raw `QueryText`, and the selected provider's query-preparation strategy is applied at the adapter boundary.
And when a post is indexed, the selected provider's passage-preparation strategy is applied independently; query preparation is never applied to passages.
And changing query preparation does not change any `IndexRecipe` or `ContentFingerprint`, while changing passage preparation does change the `IndexRecipe`.

### Embedding provider artifact selection

Each provider is its own Gradle module (ADR 0010): `providers/onnx`, `providers/gemma2` (in process)
and `providers/gemma2-server` (LM Studio). One build produces one distribution per provider:
`blog-mcp-onnx.jar`, `blog-mcp-gemma2.jar` and `blog-mcp-gemma2-server.jar`. The provider is chosen
by picking the jar to run. There is no build property, no runtime provider selector and no implicit
default. Each provider module owns its pinned profile and matching dependencies/assets. Runtime
discovery is from the provider on the jar's classpath, not from the data directory.

**AC-SRCH-40: Each distribution contains exactly one provider.**
Given the build runs without any provider property,
When Gradle assembles the distributions,
Then each provider module produces its own runnable jar, containing `core`, that provider, its services entry and its required dependencies/assets, and no other provider's classes, services entry or model library.
And no provider-selection runtime environment variable is read.

**AC-SRCH-41: Retired (ADR 0010).** The build no longer takes a provider property, so there is nothing to reject.

**AC-SRCH-42: The distribution's provider is discovered exactly once.**
Given a valid distribution whose runtime classpath contains exactly one provider module,
When the app assembles search,
Then classpath discovery finds exactly one provider and uses its built-in profile, tokenizer, model identity and budget.
And zero or multiple discovered providers, or unusable pinned assets, fail startup rather than falling back.
And Gemma2 requires no Gemma2-specific runtime environment variables or config-file values.

**AC-SRCH-43: Providers are separate modules, and core depends on no model library.**
Given the `core` module and the provider modules,
When they compile,
Then `core` compiles without any embedding-model library on its classpath, and each provider module compiles against `core` and its own model library only.
And no provider module depends on `nz.sounie.blogmcp.app`.

**AC-SRCH-44: One selected provider instance serves both consumer-owned ports.**
Given exactly one provider is discovered,
When the application assembles search,
Then that same provider instance implements `PassageEmbedder` (`search.domain.embedding`) and `QueryEmbedder` (`search.application`) without merging or relocating those ports.
And it applies independent provider-specific query and passage preparation so both vector types belong to the same model space.

**AC-SRCH-45: Search compares vectors only within the selected embedding profile.**
Given a persisted index was created with a different provider or embedding-affecting profile,
When the distribution built with the current provider profile starts,
Then entries from the other profile are not loaded or compared with current query vectors, and catalog posts become searchable in the current profile only after indexing with that profile.

**AC-SRCH-46: Query and passage preparation are selected per provider.**
Given a provider is selected,
When a query and a passage are embedded,
Then the selected provider applies its configured query-preparation strategy only to the raw query and its independently configured passage-preparation strategy only to canonical passage content.
And tests for each configured provider verify the exact prepared inputs; no provider-specific prefix or template is asserted as shared domain behavior.
And for the owner-confirmed local Google EmbeddingGemma 2 provider, its query input uses `task: search result | query: ` and its document input uses `title: {title} | text: {content}` (with `title: none` for an absent title), as documented by that model card.

**AC-SRCH-47: Both Gemma2 providers use the pinned tokenizer and conservative, provider-specific budget.**
Given either Gemma2 distribution, with the tokenizer pinned to revision `914f7f89142e33e77833254d9c9b90c3cef7303b`,
When the provider loads its tokenizer and validates its built-in profile,
Then the tokenizer assets come from that revision, the native dimension is 768, and the documented context is 8,192 tokens.
And the body, overlap, title and fully prepared passage budgets are 3,000, 256, 128 and 4,096 respectively, and tests using the pinned tokenizer assert `0 <= overlap < body`, `body <= fullyPrepared`, `title <= fullyPrepared`, and `fullyPrepared <= 8,192`.
And tests assert this is the Gemma2 profile and does not inherit ONNX BGE's values.

**AC-SRCH-48: Missing or unusable pinned tokenizer assets fail Gemma2 startup.**
Given a Gemma2 distribution whose pinned tokenizer assets cannot be loaded,
When the application resolves the provider profile,
Then startup fails with a provider configuration error and does not start with a partial profile or substitute another provider.
And the tokenizer asset's Apache 2.0 license and notices are preserved in each Gemma2 distribution.

**AC-SRCH-49: The LM Studio provider records its pinned model identity without claiming endpoint attestation.**
Given the LM Studio distribution (`blog-mcp-gemma2-server.jar`), whose local endpoint does not report a verifiable loaded revision,
When the application records provider identity and embeds passages or queries,
Then the profile identifies Google EmbeddingGemma 2 at revision `914f7f89142e33e77833254d9c9b90c3cef7303b`, while making no claim that the endpoint independently proved it.
And its tokenizer assets are pinned to that exact revision.
(The in-process provider records its own ONNX revision instead; see AC-SRCH-51.)

**AC-SRCH-50: Provider-keyed indexes are distinct, stable across restarts and isolated when switching profiles.**
Given ONNX BGE and Gemma2 have each written entries under their stable provider/profile key,
When an application restarts with the same profile, switches to the other profile, or both provider-specific distributions run against the same data directory concurrently,
Then the same profile reuses its persisted entries from its own index subtree.
And switching profiles neither loads, deletes, quarantines nor overwrites entries under the other profile's subtree.
And concurrent runs using different profile keys write only within their respective subtrees.

**AC-SRCH-51: The in-process Gemma2 provider runs without a network or a separate service.**
Given `blog-mcp-gemma2.jar`, with no network access and no model server running,
When the app starts, indexes posts and answers a `search_posts` query,
Then it embeds with the bundled q8 ONNX model from `onnx-community/embeddinggemma-2-ONNX` at revision `daa72c51243991dfcaf9f9137d2c573d8f7790c0`, returning 768-value normalised vectors from the `sentence_embedding` output.
And the jar carries the model files with their pinned SHA-256, and each process extracts them on first use into its own new temporary directory, removed when the JVM exits.
And its index lives under the `gemma2-onnx-q8-daa72c51-v1` subtree, separate from the LM Studio provider's `gemma2-914f7f89-v1`.
And it uses the same query and passage templates, tokenizer and budget as the LM Studio provider (AC-SRCH-46, AC-SRCH-47).

**AC-SRCH-52: The distributions run on Linux, Windows and macOS.**
Given a Linux x64 CI runner,
When `check` and `jarTest` run for every provider module,
Then they pass.
And the jars bundle native libraries for Linux x64 and arm64, Windows x64 and macOS arm64. Only Linux x64 is tested in CI; macOS arm64 is the development platform.

### Index maintenance

**AC-SRCH-11: Publishing indexes a post.**
Given an empty index,
When `CatalogPostPublished` for `sounie-wp:1` (700-word body) is delivered,
Then the index holds `sounie-wp:1` with 3 chunks, its metadata (site, URL, title, tags and dates) and its fingerprint, the outcome is `ADDED`, and a search can return it.

**AC-SRCH-12: Indexing is idempotent.**
Given `sounie-wp:1` is already indexed from the same content,
When the same `CatalogPostPublished` or `CatalogPostRevised` is delivered again,
Then the outcome is `UNCHANGED`, the embedder is not called, and the index is identical to before.

**AC-SRCH-13: A content revision replaces the post's chunks.**
Given `sounie-wp:1` indexed with 3 chunks,
When `CatalogPostRevised` arrives with a 120-word body (or the same body with a new title),
Then the outcome is `RE_EMBEDDED`, the post has exactly the new chunks (1 chunk, or 3 chunks whose passages carry the new title), no chunk of the old version remains, and other posts are untouched.

**AC-SRCH-14: A metadata-only revision does not re-embed.**
Given `sounie-wp:1` indexed,
When `CatalogPostRevised` arrives where only the tags, canonical URL or published-at differ, and the title and body are unchanged (and the post is still `FULL`),
Then the outcome is `METADATA_REFRESHED`, the embedder is not called, the chunks and fingerprint are unchanged, and results and filters use the new metadata (e.g. a date filter uses the new published-at).

**AC-SRCH-15: A revision for a post that is not indexed adds it.**
Given an index without `elegant:42`,
When `CatalogPostRevised` for `elegant:42` is delivered,
Then the post is indexed and the outcome is `ADDED`.

**AC-SRCH-16: Withdrawal removes the post.**
Given `sounie-wp:1` indexed with 3 chunks,
When `CatalogPostWithdrawn` for it is delivered,
Then no chunk of `sounie-wp:1` remains, it is never returned by a search, and the outcome is `REMOVED`.
And when the same withdrawal is delivered again (or a withdrawal arrives for a post that was never indexed), the outcome is `ALREADY_ABSENT` and nothing changes.

**AC-SRCH-17: An embedder failure never breaks the catalog or loses the old entry.**
Given `sounie-wp:1` indexed, and an embedder that throws `EmbedderUnavailable`,
When `CatalogPostRevised` with a new body is published on the bus,
Then the publisher's `publish` call returns normally, the old chunks of `sounie-wp:1` are still indexed and searchable, and the failure is logged to stderr.
And a later reconcile with a working embedder re-embeds the post (AC-SRCH-28).

**AC-SRCH-18: A malformed published-language payload is rejected without effect.**
Given a `CatalogPostPublished` whose post ID is not `<siteId>:<sourcePostId>`, whose site ID differs from the post ID's site part, or whose completeness is neither `FULL` nor `SUMMARY`,
When it is delivered,
Then `PostToIndex.of` raises `MalformedCatalogPost`, the listener logs it and does not rethrow, and the index is unchanged.

### Search query

**AC-SRCH-19: A blank or overlong query is rejected.**
Given query text `""`, `"   "` or `"\n\t"`, or a text of 1,001 characters after trimming,
When a `SearchQuery` is created,
Then `InvalidSearchQuery` is raised with reason `BLANK` or `TOO_LONG` respectively. Text of exactly 1,000 characters is accepted, and surrounding whitespace is trimmed.

**AC-SRCH-20: The limit is clamped to 1..20.**
Given a requested limit of `-5`, `0`, `1`, `20` or `21`,
When a `ResultLimit` is created,
Then it is 1, 1, 1, 20 and 20 respectively, and `ResultLimit.defaultLimit()` is 10.

**AC-SRCH-21: The date range must be ordered.**
Given `from = 2024-05-01` and `to = 2024-04-30`,
When a `PublishedDateRange` is created,
Then `InvalidSearchQuery` is raised with reason `FROM_AFTER_TO`. `from = to` (a single day), only `from`, only `to`, and neither are all valid.

### Search

**AC-SRCH-22: An empty index returns no results.**
Given an empty index (or filters that exclude every post),
When a valid query is searched,
Then the results are empty and no error is raised.

**AC-SRCH-23: Hits are grouped by post and ranked by the best chunk, and the snippet is that chunk.**
Given post A with chunks scoring 0.90 and 0.80, and post B with one chunk scoring 0.85,
When searched,
Then the results are `[A (0.90), B (0.85)]`, A appears once, and A's snippet is the text of its 0.90 chunk, without the title line.

**AC-SRCH-24: The limit counts posts, not chunks.**
Given 25 indexed posts, each with 3 chunks,
When searched with limit 20,
Then exactly 20 distinct posts are returned, in ranking order.

**AC-SRCH-25: Ties are broken deterministically.**
Given posts `elegant:9` and `sounie-wp:3` with equal best scores and different `publishedAt`,
Then the more recently published post ranks first.
And given equal scores and equal `publishedAt`, `elegant:9` ranks before `sounie-wp:3` (post ID ascending).
And given two chunks of one post with equal scores, the snippet is from the lower chunk index.
And the same index and query always give the same order.

**AC-SRCH-26: The site filter.**
Given posts from `sounie-wp` and `elegant`,
When searched with `OnlySite(elegant)`,
Then only `elegant` posts are returned. With `AnySite`, posts from both are eligible. A site with no posts gives empty results.

**AC-SRCH-27: The published-date filter is inclusive, by `Pacific/Auckland` calendar day.**
Given four posts (NZ daylight saving ended on 2024-04-07, so 1 April 2024 is NZDT, UTC+13, and 30 April is NZST, UTC+12):
- P1 published at `2024-03-31T10:59:59Z` (31 March, 23:59:59 NZDT);
- P2 published at `2024-03-31T11:30:00Z`. That is **1 April**, 00:30 NZDT, early in the NZ day, even though it is still 31 March in UTC;
- P3 published at `2024-04-30T11:59:59.999Z` (30 April, 23:59:59.999 NZST);
- P4 published at `2024-04-30T12:00:00Z` (1 May, 00:00 NZST).

When searched with `to = 2024-03-31`, only P1 is eligible;
with `from = 2024-04-01`, P2, P3 and P4 are eligible. **P2 is in range** although its UTC date is 31 March;
with `from = 2024-04-01` and `to = 2024-04-30`, P2 and P3 are eligible;
and with `from = to = 2024-04-01`, only P2 is.
And `PublishedDateRange` takes no zone argument: the zone is the constant `PublishedDateRange.ZONE`.

### Reconcile and rebuild

**AC-SRCH-28: A reconcile adds missing posts, re-embeds stale ones, refreshes metadata and removes orphans.**
Given a catalog with posts P1 (not indexed), P2 (indexed with an older body), P3 (indexed, only its tags differ) and P4 (indexed and identical), and an index that also holds an orphan O (not in the catalog),
When `ReconcileIndex` runs,
Then P1 is `ADDED`, P2 is `RE_EMBEDDED`, P3 is `METADATA_REFRESHED` without calling the embedder, P4 is `UNCHANGED`, and O is `REMOVED`. The report counts 1 of each outcome, and the index then holds exactly P1–P4.
And running it a second time gives only `UNCHANGED`.

**AC-SRCH-29: A recipe change re-embeds everything, and a rebuild is a reconcile.**
Given an index built under recipe R1, and a current `IndexRecipe` R2 (e.g. a different chunking parameter or model ID),
When `ReconcileIndex` runs,
Then every catalog post is `RE_EMBEDDED`.
And given an empty index (a fresh start), a reconcile adds every catalog post.

**AC-SRCH-30: A reconcile carries on past a failure, and an empty catalog empties the index.**
Given a catalog of 3 posts that are not indexed, and an embedder that fails only for the second post's passages,
When `ReconcileIndex` runs,
Then posts 1 and 3 are `ADDED`, post 2 is `FAILED` and listed in the report, and nothing else changes.
And given a catalog with no posts and an index holding 2 posts, both are `REMOVED` (see Q8).

**AC-SRCH-31: The catalog serves its current posts through the shared contract.**
Given the catalog has stored `sounie-wp:2`, `elegant:7` and `sounie-wp:10`,
When `CatalogPosts.currentPosts()` is called,
Then it returns three `CatalogPostState`s with the full stored state, including `completeness` (`FULL` or `SUMMARY`), sorted by post ID (`elegant:7`, `sounie-wp:10`, `sounie-wp:2`, as plain string order). Summary-only posts are listed too. An empty catalog returns an empty list.

### Summary-only posts are excluded (owner decision, Q7)

**AC-SRCH-33: Publishing a summary-only post indexes nothing.**
Given an empty index,
When `CatalogPostPublished` for `elegant:5` with completeness `SUMMARY` is delivered,
Then the decision is `IndexDecision.Exclude`, the outcome is `EXCLUDED`, the embedder is not called, the index has no entry for `elegant:5`, and no search returns it.

**AC-SRCH-34: A revision from summary to full adds the post.**
Given `elegant:5` was published as `SUMMARY` (so it is not indexed),
When `CatalogPostRevised` arrives with completeness `FULL` and the full body (as after catalog AC-CAT-14),
Then the post is chunked and embedded, the outcome is `ADDED`, and a search can return it.

**AC-SRCH-35: A revision from full to summary removes the post.**
Given `elegant:5` is indexed with 3 chunks from a `FULL` body,
When `CatalogPostRevised` arrives with completeness `SUMMARY`,
Then the decision is `Exclude`, every chunk of `elegant:5` is removed, the outcome is `REMOVED`, the embedder is not called, and no search returns it.
And when the same revision is delivered again, the outcome is `EXCLUDED` and nothing changes.

**AC-SRCH-36: A reconcile excludes summary-only posts.**
Given a catalog holding S1 (`SUMMARY`, not indexed), S2 (`SUMMARY`, but indexed from an earlier `FULL` version) and F1 (`FULL`, not indexed),
When `ReconcileIndex` runs,
Then S1 is `EXCLUDED` and is **not** added, S2 is `REMOVED`, and F1 is `ADDED`. The embedder is called only for F1. The report gives each post's outcome. S2's `Exclude` removed an existing entry, so it is reported as `REMOVED`, the same outcome an orphan removal reports. No orphan `Remove` is planned for S1 or S2, because they are in the catalog. Running the reconcile again gives `EXCLUDED` for S1 and S2 and `UNCHANGED` for F1.

**AC-SRCH-37: `Completeness` alone decides indexability.**
Given any combination of existing entry (absent, same fingerprint, or different fingerprint) and post to index,
When `IndexDecision.forPost` is called,
Then for completeness `SUMMARY` the result is always `Exclude`, and for `FULL` it is exactly what the normal decision gives (`Add`, `ReEmbed`, `RefreshMetadata` or `Keep`). This is tested once on `Completeness`, and `IndexPost` and `ReconcilePlan` both reach it only through `IndexDecision.forPost`.

### Malformed catalog posts during a reconcile (review B1)

**AC-SRCH-38: A malformed catalog post never causes an index removal.**
Given `sounie-wp:4` indexed, and a catalog whose `CatalogPostState` for `sounie-wp:4` is malformed but has a readable post ID (e.g. completeness `PARTIAL`, or a null body),
When `ReconcileIndex` runs,
Then `SharedCatalogPosts` returns `Unreadable(sounie-wp:4, reason)`, the plan's decision is `IndexDecision.Unreadable`, the existing entry for `sounie-wp:4` is unchanged, the embedder is not called for it, and the report lists `sounie-wp:4` as `FAILED` with the reason. `sounie-wp:4` is **not** treated as an orphan.
And given a catalog that also contains a state whose post ID cannot be read (e.g. `not-an-id`, or a null post ID), and an index holding an orphan O (absent from the catalog),
When `ReconcileIndex` runs,
Then the `Unidentified` entry produces no decision, O is **not** removed in that run, the report carries `ORPHAN_REMOVAL_SUPPRESSED` with the unidentified entry's reason, and the other readable posts are still added, re-embedded, refreshed or excluded as usual.
And when a later reconcile has no unidentified entry, O is `REMOVED`.
And given a catalog listing the post ID `sounie-wp:4` twice, the plan contains exactly one `IndexDecision.Unreadable` for it, whose reason mentions "duplicate". Its existing entry is unchanged, it is reported `FAILED`, and every other post is still planned and applied (the reconcile is not aborted).

### Snippet trimming (slice 3; proposed in `docs/domain/app.md`, pending owner approval)

**AC-SRCH-39: A snippet is at most 60 words of the best chunk.**
Given a best chunk of 300 words `w1 … w300`,
When a `PostMatch` is built,
Then its snippet text is `w1 … w60` followed by ` …`, and `truncated()` is true.
And given a chunk of exactly 60 words, or of 40 words, the snippet is the chunk text unchanged and `truncated()` is false.
And given an empty chunk (an empty-body post), the snippet is empty.
(AC-SRCH-23's fixtures use chunks of 60 words or fewer, so its expectation, "the text of its 0.90 chunk", still holds.)

### Image embedding (ADR 0011, phase 1)

Where the tests live:
- **Core** (`core/src/test`): AC-SRCH-53 and 54 (`ImagePixels`, pure unit tests, under PIT).
- **Each provider module**: AC-SRCH-55 (each provider's own answer to `imageEmbedder()`).
- **`providers/gemma2`**, without the model: AC-SRCH-56 to 61, and the count check in AC-SRCH-65.
  AC-SRCH-61 needs only the fixtures and the reference JSON.
- **`providers/gemma2`**, `@Tag("model")`, run with `-PhuggingFaceMirror=.hf-mirror`: AC-SRCH-62 to 64.

The fixtures and `gemma2-image-reference.json` are in `core/src/test/resources/`. Test code decodes a
fixture into `ImagePixels` by reading its RGB samples and ignoring any alpha, as PIL's
`convert("RGB")` does. It must not paint the image onto a background.

**AC-SRCH-53: Image pixels hold an immutable RGB raster.**
Given width 2, height 1 and the bytes `[255, 0, 0, 0, 0, 255]` (a red pixel, then a blue one),
When `ImagePixels` is created,
Then its width is 2, its height is 1, and `rgb()` returns those six bytes.
And changing the caller's array afterwards, or the array `rgb()` returned, does not change the image.
And two images with the same dimensions and bytes are equal and have equal hash codes. Images that
differ in one byte, or that have the same bytes but swapped dimensions (1 × 2), are not equal.
And a 1 × 1 image is accepted, and so is a 4,096 × 4,096 image (exactly 16,777,216 pixels).

**AC-SRCH-54: Invalid image pixels are rejected.**
Given width 0, height 0, or a negative width or height,
When `ImagePixels` is created,
Then `InvalidImagePixels` is raised with reason `NON_POSITIVE_DIMENSION`.
And given 4,096 × 4,097, or 65,536 × 65,536 (whose `int` product would overflow), with a 3-byte
array, the reason is `TOO_MANY_PIXELS`, without first complaining about the data length.
And given 2 × 2 with 11 or 13 bytes, the reason is `DATA_LENGTH_MISMATCH`, and a `null` array raises
`NullPointerException`.

**AC-SRCH-55: Each provider declares its image capability.**
Given the provider of each distribution,
When `imageEmbedder()` is called,
Then `onnx` (BGE) and `gemma2-server` (LM Studio) return empty, and the in-process `gemma2` returns an
image embedder.
And the call loads no model and makes no network call.

**AC-SRCH-56: The target size fits the patch budget and is a multiple of 48.**
Given an image of 1,024 × 768 (4:3), or 1,600 × 1,200,
When `TargetSize.of` is computed,
Then it is 912 × 672, with a patch grid of 57 columns × 42 rows, 2,394 real patches and 266 image
tokens.
And given a portrait 768 × 1,024, it is 672 × 912, with 42 columns × 57 rows and the same counts.
And given a square or tiny image (1,000 × 1,000, 64 × 64 or 1 × 1), it is upscaled or downscaled to
768 × 768, with 48 × 48 patches, 2,304 real patches and 256 image tokens.
And given a wide 4,000 × 100 (40:1), it is 5,040 × 96, with 315 × 6 patches, 1,890 real patches and
210 image tokens.
And for every size tried, both sides are positive multiples of 48 and the real patches are at most
2,520.

**AC-SRCH-57: A side that floors to zero uses the reference processor's fallback.**
Given an extreme aspect ratio where one side's scaled length is below 48, e.g. 10,000 × 10 or
10 × 10,000,
When `TargetSize.of` is computed,
Then the short side is 48, and the long side is the reference processor's value, capped at 13,440
((2,520 ÷ 9) × 48). The expected sizes are 13,440 × 48 and 48 × 13,440, which give 2,520
real patches and 280 image tokens.
(Confirmed against transformers main, `image_processing_pil_gemma4.py`,
`get_aspect_ratio_preserving_size`: a single side flooring to 0 becomes 48, and the other becomes
min(floor(long ÷ short) × 48, 13,440). Both sides can never floor to zero, because the scaled area is
at least 48 × 48; the reference raises `ValueError` in that case.)

**AC-SRCH-58: The resize is PIL's antialiased bicubic.**
Given a 1,024 × 768 image of the single colour (10, 200, 30),
When it is resized to 912 × 672,
Then every pixel is still (10, 200, 30).
And given an image whose size already equals the target, the result has the same bytes.
And given a 96 × 96 image of alternating black and white one-pixel columns, resized to 48 × 48,
every pixel outside the two outermost columns at each edge is grey, 127 or 128 in every channel,
because downscaling widens the kernel. Unfiltered sampling would give 0 or 255.
And given a 4 × 1 image `[black, black, white, white]` resized to 8 × 1, every sample stays within
0..255 (the bicubic overshoot is clamped), and at least one is 0 and one is 255.
And the result is a `ResizedImage` whose size equals the target size.

**AC-SRCH-59: Patchify cuts, rescales, positions and pads.**
Given a 48 × 48 resized image whose pixel at `(x, y)` is `(x, y, 255)`,
When it is patchified,
Then `pixel_values` has 2,520 rows of 768 values. Row `k` for `k < 9` is the patch at column
`k mod 3`, row `k div 3`. Inside a patch, value `(py × 16 + px) × 3 + c` is that pixel's channel `c`
divided by 255, so 255 becomes exactly 1.0 and 0 becomes 0.0, with no further normalisation.
And position id `k` is `(k mod 3, k div 3)` for `k < 9`.
And rows 9 to 2,519 are all 0.0, with position `(−1, −1)`.
And the patch grid is 3 × 3, with 9 real patches and 1 image token.
And a 912 × 672 resized image gives exactly 2,394 real patches and 126 padding patches.

**AC-SRCH-60: Image input ids wrap the image tokens.**
Given a patch grid with 266 image tokens,
When the image input ids are built,
Then they are 270 ids: `2`, `255999`, then 266 × `258880`, then `258882`, `1`. There is no task
prefix.
And given 1 image token, they are `[2, 255999, 258880, 258882, 1]`.

**AC-SRCH-61: Preprocessing matches the reference for all six fixtures.**
Given each of the six fixtures in `gemma2-image-reference.json`, decoded into `ImagePixels`,
When `Gemma2ImagePreprocessor` runs,
Then the decoded width and height equal the JSON's, and the input ids, `patches` (2,520),
`realPatches`, `patchGrid` (`[columns, rows]`) and `imageTokens` equal the JSON's exactly.
And the sum of all `pixel_values` (padding included), accumulated in `double`, is within a relative
1e-4 of `pixelValuesSum` (tolerance accepted by the owner, 2026-10-13).
(A pixel sum cannot detect a wrong patch or channel order. AC-SRCH-59 and AC-SRCH-62 catch that.)

**AC-SRCH-62: Java image embeddings match the official model.**
Given the gemma2 provider's image embedder and each of the six fixtures,
When `embedImage` is called,
Then the embedding has 768 values and unit length, and its cosine with the fixture's reference
`embedding` is at least 0.994 (ADR 0011 Parity).
And the lowest measured cosine is written into ADR 0011's Parity row "Image embeddings, Java vs
official" in the same PR.

**AC-SRCH-63: Each test query ranks its intended image first.**
Given the six Java image embeddings from AC-SRCH-62, and the six reference queries embedded through
the same provider's `embedQuery` (with the Gemma2 query prefix),
When each query is compared with every image,
Then each query's highest similarity is its intended image, for 6 of 6. The pairs are stated in
the test by file name, because the JSON lists queries and images in different orders:
- "a black swan swimming on a lake" with `black-swan-in-reeds.png`;
- "grapes growing on a vine in the garden" with `grapes-on-trellis.png`;
- "motor racing at a race track" with `race-car-at-circuit.png`;
- "pub quiz question about sloths" with `quiz-slide-sloth.png`;
- "rugby match in a stadium at night" with `rugby-stadium-smoke.png`;
- "managing images in a web service" with `image-management-services.jpg`.

Only the order is asserted, never a score.

**AC-SRCH-64: The vision encoder is bundled and runs offline.**
Given the gemma2 provider, with no network access,
When an image is embedded,
Then the q8 `vision_encoder_quantized.onnx` and `.onnx_data` from
`onnx-community/embeddinggemma-2-ONNX` at revision `daa72c51243991dfcaf9f9137d2c573d8f7790c0` are read
from the provider's bundled `embeddinggemma-2-onnx/` resources. They are pinned like the text model
(AC-SRCH-51), with checksums in `gradle/verification-metadata.xml`, and extracted into the process's
temporary model directory.
And the provider's model identity, profile key and index recipe are unchanged by phase 1, so existing
text indexes stay valid.

**AC-SRCH-65: Image failures surface as an unavailable embedder, and text is unchanged.**
Given a vision output whose row count differs from the patch grid's image tokens,
When the count is checked,
Then it fails with `IllegalStateException`, and `embedImage` reports it as `EmbedderUnavailable`.
This check is tested without the model. The conversion to `EmbedderUnavailable` and the lock
release are the existing `Gemma2OnnxEmbedder.withModel` path, which image calls reuse.
And given a valid image too tall and narrow to resize within 48 MiB (e.g. 1 × 16,777,216, see 3.6),
When it is embedded,
Then preprocessing refuses it with `ImageTooLargeToResize`, and `embedImage` reports it as
`EmbedderUnavailable`. Any other preprocessing failure is reported the same way. Preprocessing runs
outside the model lock, and this is tested without the model.
And text embeddings are unchanged: the existing gemma2 text tests (AC-SRCH-51) pass with the same
vectors, and text calls still pass empty media tensors.

### Indexing post images (ADR 0011, phase 3; approved and implemented)

Where the tests live:
- **Domain** (pure, under PIT): AC-SRCH-67 to 73, with the `FakeEmbedder` extended to implement
  `ImageEmbedder` and an in-memory `ImageSource` fake that returns a scripted `ImageFetch` per URL.
- **Adapters**: AC-SRCH-66 (both anti-corruption adapters), AC-SRCH-74 (real Jackson), AC-SRCH-75
  (`app.mcp`), and AC-SRCH-76 to 80 against WireMock over https with real `ImageIO` (no double of
  `HttpClient` or `ImageIO`). `AddressCategory` is also unit-tested alone, and the WireMock tests use the `HostResolver` seam.

Unless an AC says otherwise, "with images" means the `WithImages` strategy and "text-only" means
`TextOnly`. Post images are written (URL, alt, caption).

**AC-SRCH-66: Post images reach `PostToIndex` from events and from reconciles.**
Given a `CatalogPostPublished`, and a `CatalogPostState`, each with images
[(`https://i0.wp.com/a.jpg`, `Swan`, ``), (`https://blog2.sounie.nz/b.png`, ``, `Grapes`)],
When each is translated,
Then the `PostToIndex` has those two post images in that order.
And an image with a null field, or a URL that is not absolute `https` (e.g. `http://x.example/a.jpg`),
makes the payload malformed: the event is rejected without effect (as in AC-SRCH-18), and the state
becomes `Unreadable(postId, reason)` (as in AC-SRCH-38).

**AC-SRCH-67: An image's description is its alt text, then its caption, then its URL.**
Given post images (`https://x.example/a.jpg`, `A black  swan`, `In reeds`),
(`https://x.example/a.jpg`, `  `, `In reeds`) and (`https://x.example/a.jpg`, ``, ``),
When their descriptions are taken,
Then they are `A black swan`, `In reeds` and `Image: https://x.example/a.jpg`.
And a description of 70 words gives a snippet of its first 60 words plus ` …` (AC-SRCH-39's rule).

**AC-SRCH-68: Text-only providers fetch nothing, and their indexes stay valid.**
Given text-only image indexing, and a post with two images,
When it is indexed and its recipe and fingerprint are computed,
Then the image source is never called, the indexed post has no images, and the recipe ID and the
fingerprint are exactly those the same post gets with no images (and those of the code before phase 3).
And an images-only revision of an indexed post is `UNCHANGED`, with no embedder call.

**AC-SRCH-69: Each fetched image is its own entry, embedded alone.**
Given image indexing, and a post with a 120-word body and images `a.jpg` and `b.png` that both fetch,
When it is published,
Then the outcome is `ADDED`, the post has 1 chunk and 2 indexed images, with indexes 0 and 1, the URLs,
their descriptions, and the image embedder's vectors. `embedImage` was called once per image, and the
passage embedder received only text passages.
And the recipe ID is the text-only recipe ID followed by `/images-v1`.
And restoring an indexed post whose image indexes are `[1, 0]`, `[0, 0]` or `[-1]` raises
`InvalidIndexedPost`, while `[0, 2]` is accepted.

**AC-SRCH-70: Image changes re-embed under image indexing.**
Given image indexing and a post indexed with images [`a.jpg` (alt `Swan`)],
When a revision arrives that adds `b.png`, removes `a.jpg`, or changes `a.jpg`'s alt text to `Black swan`
(title and body unchanged),
Then each time the outcome is `RE_EMBEDDED`, and the stored images and descriptions match the revision.
And the same revision delivered again is `UNCHANGED`.

**AC-SRCH-71: A rejected image is skipped for good.**
Given image indexing and a post with images `a.jpg` (fetches) and `pixel.gif` (`Rejected(TOO_SMALL)`),
When it is published,
Then the outcome is `ADDED`, the post has one indexed image, `a.jpg` with index 0, and no entry for
`pixel.gif`.
And a reconcile afterwards gives `UNCHANGED` and does not call the image source.

**AC-SRCH-72: An unavailable image is retried until it is settled.**
Given image indexing and a post with images `a.jpg` (fetches) and `b.png` (`Unavailable`),
When it is published,
Then the outcome is `ADDED`, the post is searchable by its text and by `a.jpg`, and it has no entry for
`b.png`.
And when a reconcile runs while `b.png` is still unavailable, the post is `RE_EMBEDDED` and still has
only `a.jpg`.
And when a reconcile runs once `b.png` fetches, the post is `RE_EMBEDDED` with both images (indexes 0
and 1), and the next reconcile is `UNCHANGED`.
And the same holds when `b.png` fetches but `embedImage` throws `EmbedderUnavailable` for it.
And a passage-embedder failure still fails the whole post and keeps its old entry (AC-SRCH-17).

**AC-SRCH-73: A post scores as its best entry, which may be an image.**
Given post A with chunks scoring 0.70 and 0.60 and an image (`https://x.example/swan.jpg`, alt
`Black swan`) scoring 0.78, and post B with one chunk scoring 0.75,
When searched,
Then the results are `[A (0.78), B (0.75)]`, A's snippet is `Black swan` and its matched image is
`https://x.example/swan.jpg`, and B has no matched image.
And given a chunk and an image of one post with equal scores, the chunk is the best entry; given two
images with equal scores, the lower image index is.
And a post with zero chunks (blank title and body) but one indexed image can be returned through that
image.

**AC-SRCH-74: Index files move to format 2 with images, and format-1 files still load.**
Given an indexed post with 2 chunks and 2 images (indexes 0 and 2), saved by `FileVectorIndex`,
When the index is reopened,
Then the post is restored with the same images, descriptions and bit-identical vectors, and the file has
`"format": 2` and an `images` array.
And given a valid `"format": 1` file (no `images`), it loads with no images, is not quarantined, and a
text-only reconcile then gives `UNCHANGED`.
And a file with any other format, or with an image vector of the wrong dimension, is quarantined.

**AC-SRCH-75: `search_posts` results show the matched image.**
Given search results where one post's best entry is an image and another's is a chunk,
When `search_posts` returns,
Then the image match has `matchedImage` set to the image URL, and the chunk match has no
`matchedImage` key at all. The output schema declares `matchedImage` as an optional string, and the
results still validate against it.

**AC-SRCH-76: JPEG, PNG and GIF images are fetched as RGB pixels.**
Given an `HttpImageSource`, and a post URL on `localhost` (a `LOOPBACK` blog, so WireMock's loopback images are permitted), serving a 100 × 80 JPEG, a 100 × 80 PNG with an alpha
channel, and an animated 64 × 64 GIF, each with its matching content type,
When each is fetched,
Then each is `Fetched` with those dimensions, the PNG's RGB samples are its colour samples with alpha
ignored (not composited on a background), and the GIF gives its first frame.

**AC-SRCH-77: Images come from any https host, and only over https.**
Given a `localhost` post URL and image URLs on host names unrelated to the post's or any configured
site's host (the WireMock server, reached under another host name through the test `HostResolver`),
When they are fetched,
Then they are `Fetched`: no host allow-list applies (owner decision, 2026-10-14).
And an `http://` image URL is `Rejected(NOT_HTTPS)` without any request being sent.

**AC-SRCH-78: Image addresses must be permitted by the blog's category.**
Given `AddressCategory.of`,
When it categorises sample addresses,
Then `127.0.0.1` and `::1` are `LOOPBACK`; `10.1.2.3`, `172.16.0.1`, `192.168.1.1`, `100.64.0.1`, `fd00::1`
and `::ffff:10.1.2.3` are `PRIVATE_NETWORK`; `169.254.169.254`, `fe80::1`, `0.0.0.0` and `224.0.0.1` are
`NON_ROUTABLE`; and `93.184.216.34` and `2606:4700::1111` are `PUBLIC`.
And `permits` is exactly the table in the glossary ("Address category"), tested on the enum: `PUBLIC`
permits only `PUBLIC`, `PRIVATE_NETWORK` also permits `PRIVATE_NETWORK`, `LOOPBACK` also permits
`PRIVATE_NETWORK` and `LOOPBACK`, `NON_ROUTABLE` permits `PUBLIC` and `NON_ROUTABLE`, and only
`NON_ROUTABLE` permits `NON_ROUTABLE`.
And given a public blog (post URL `https://93.184.216.34/p`) whose image host resolves to a
private-network or loopback address, the fetch is `Rejected(ADDRESS_NOT_PERMITTED)` and no request is
sent.
And given a public blog whose image host resolves to a public address (through the test
`HostResolver`) and redirects to a target resolving to a private-network or loopback address, the fetch
is `Rejected(ADDRESS_NOT_PERMITTED)` and no request reaches the target.
And given a loopback blog (a `localhost` post URL) with a loopback image, the fetch is `Fetched`; and
given a private-network or loopback blog with an image whose host resolves to a public address, the
fetch is `Fetched`.
And given a public, private-network or loopback blog with an image at `https://169.254.169.254/a.jpg`,
the fetch is `Rejected(ADDRESS_NOT_PERMITTED)`; given a non-routable blog (post host resolving to
`169.254.0.10` through the test `HostResolver`), the same image is not rejected for its address.
And given a post URL whose host does not resolve, the fetch is `Unavailable` (so it is retried on the
next reconcile).
And a redirect to an `http` URL is `Rejected(NOT_HTTPS)`.
And 3 redirects end in `Fetched`, while a 4th redirect is `Rejected(TOO_MANY_REDIRECTS)`.

**AC-SRCH-79: Size, type and pixel limits.**
Given responses that are:
- declared with `Content-Length: 10485761`, or streaming 10,485,761 bytes with no length;
- `image/webp`, `text/html`, or without a content type;
- `image/jpeg` whose bytes are not an image;
- a PNG declaring 4,097 × 4,096 pixels;
- PNGs of 63 × 200, 200 × 63 and 1 × 1 pixels;

When each is fetched,
Then they are `Rejected` with `TOO_LARGE`, `UNSUPPORTED_TYPE`, `UNDECODABLE`, `TOO_LARGE` and
`TOO_SMALL` respectively, and one line naming the URL and the reason is written to stderr for each.
And a body of exactly 10,485,760 bytes is read, and a 64 × 64 PNG is `Fetched`.
And the oversized PNG is refused without decoding its pixels.

**AC-SRCH-80: Transient failures are unavailable; permanent ones are rejected.**
Given an allowed image URL whose server answers 500, 503, 429 or 408, does not answer within the
response timeout, or refuses the connection,
When it is fetched,
Then the result is `Unavailable` with a detail naming the cause.
And when the server answers 404, 403 or 410, the result is `Rejected(HTTP_STATUS)`.

## 9. Decisions

Model decisions (approved with the model by the owner on 2026-10-02):
- Tags are **not embedded**. They are metadata only, so a tags-only revision does not re-embed (AC-SRCH-14).
  Adding them to every passage would bias every chunk towards the tag words and couple tag edits to
  re-embedding.
- A re-index is decided by the **content fingerprint**, not the event's `changed` set, so events and reconciles share one decision path.
- A post is ranked by its best chunk (max-pooling), and the snippet is that chunk.
- Publish and revise are both upserts. Indexing is idempotent.
- `IndexPost` embeds before it changes the index, so a failure keeps the old entry. The listener swallows and logs failures.
- ONNX BGE queries use the BGE query instruction, while ONNX BGE passages do not. This is a
  provider-specific strategy, not shared behavior for every embedding provider (AC-SRCH-32, 46).
- A malformed catalog post never causes an index removal during a reconcile (lead decision, review loop 1, B1; AC-SRCH-38):
  - one with a readable ID is `IndexDecision.Unreadable`: its entry is kept and it is reported `FAILED` with its reason;
  - one with an unreadable ID suppresses every orphan removal in that run (`ORPHAN_REMOVAL_SUPPRESSED`).
- Every ONNX BGE passage fits in one model partition (at most 465 of 510 content tokens), counted
  with its exact tokenizer (AC-SRCH-8). Other providers require their own verified tokenizer and
  context-derived budget (AC-SRCH-47).
- `OnnxEmbedder` serialises model access with one lock (section 4.1).

Embedding-provider decisions (AC-SRCH-40–52; model identity confirmed by the owner on 2026-10-08;
ADR 0009 accepted 2026-10-09, ADR 0010 accepted 2026-10-11; implemented):
- Each provider is its own Gradle module with its own jar (ADR 0010, which replaces ADR 0009's build
  property). The former runtime `BLOG_MCP_EMBEDDING_PROVIDER` selector is gone; none is read at runtime.
- The distribution's provider is auto-discovered from the runtime classpath. `app` owns discovery and
  composition, not a new domain aggregate or merged embedding port. `PassageEmbedder` and
  `QueryEmbedder` remain separate consumer-owned ports, implemented by the same provider instance.
- Each provider owns its pinned profile, query/passage strategies, tokenizer and budget. Both Gemma2
  providers pin tokenizer revision `914f7f89142e33e77833254d9c9b90c3cef7303b`, dimension 768 and
  context 8,192 (`Gemma2Profile`). The LM Studio provider records that revision as its model; the
  in-process provider records `onnx-community/embeddinggemma-2-ONNX@daa72c51` (q8).
- Gemma2 uses query prefix `task: search result | query: ` and document format
  `title: {title} | text: {content}` (`title: none` when absent). Its body/overlap/title/fully
  prepared limits are 3,000/256/128/4,096 tokens, validated with the pinned tokenizer. They are
  provider-specific and do not inherit ONNX BGE limits (400/100/64/465). The tokenizer is
  approximately 32.2 MB, usable with existing DJL 0.36.0 HuggingFace tokenizers, Apache 2.0; package
  with required notices preserved.
- The provider SPI is `nz.sounie.blogmcp.search.application.embedding.spi`, consumed by `app` and
  implemented by each provider module. Neither SPI nor provider depends on `app`; domain and search application depend
  on no provider implementation.
- Query preparation is excluded from `IndexRecipe`; passage preparation and other indexing-affecting
  profile identity remain included. A stable profile key selects a distinct persistent index subtree;
  the same profile reuses it after restart, while switching profiles cannot load, delete or overwrite
  another profile's entries. The keys and paths (`search/indexes/<profileKey>/…`) are in
  `docs/domain/app.md` and ADR 0010 decision 8.
- No domain event is emitted for a startup composition choice, and no aggregate changes.

Image embedding decisions (ADR 0011 phase 1, AC-SRCH-53 to 65; approved by the owner, 2026-10-14):
- The preprocessing, runtime, threshold and scope are fixed by ADR 0011 and
  `docs/plans/image-embeddings.md`, and are not reopened here.
- `ImagePixels` is a domain value object (`search.domain.embedding`), so phase 3's `PostIndexer` can
  carry it. `ImageEmbedder` is a domain port in the same sub-package, next to `PassageEmbedder`
  (see Q-IMG 3 and 6.1).
- `imageEmbedder()` is abstract in the SPI, with no default, so a new provider cannot be text-only by
  accident.
- Preprocessing parity (AC-SRCH-61) needs no model, so it runs in the normal test task. Only the
  embedding and ranking checks are `@Tag("model")`.
- Owner decisions on the Q-IMG questions (all resolved 2026-10-13, owner):
  1. **Pixel cap (resolved):** keep 16,777,216 pixels (4,096 × 4,096) in `ImagePixels` (AC-SRCH-53, 54).
  2. **`pixelValuesSum` tolerance (resolved):** a relative 1e-4 is accepted (AC-SRCH-61).
  3. **Port placement (resolved):** `ImageEmbedder` lives in `search.domain.embedding`, next to
     `PassageEmbedder`, because phase 3's `PostIndexer`, a domain service, will call it (ADR 0006).
  4. **Resize test vector (resolved):** no exact resize test vector is added; AC-SRCH-61 and 62 cover
     resize parity with the reference.
- Reference behaviour confirmed by the researcher from transformers main
  (`image_processing_pil_gemma4.py`: `get_aspect_ratio_preserving_size`, `convert_image_to_patches`,
  `_preprocess`, `pad_along_first_dim`), so AC-SRCH-57 and 59 are confirmed:
  - a single side flooring to 0 becomes 48 (`side_mult`), and the other becomes
    min(floor(long ÷ short) × 48, 13,440), where 13,440 = (2,520 // 9) × 48; both sides zero raises
    `ValueError`;
  - values inside a patch are ordered (p_h, p_w, C); patches are row-major; position ids are
    (x = column, y = row);
  - padding patches are 0 in every pixel value, with position −1.

Image indexing decisions (ADR 0011 phase 3, AC-SRCH-66 to 80; approved by the owner, 2026-10-14):
- The fixed decisions of ADR 0011 and the plan (each image its own entry, best-entry scoring, the
  snippet fallback, https only, 10 MB, JPEG/PNG/GIF, WebP skipped) are not reopened, except as
  below.
- **No host allow-list (owner decision, 2026-10-14).** Blogs may serve images from arbitrary domains
  of their own, so post images are fetched from any `https` host. This replaces the "configured
  sites' hosts and their platform media CDNs" part of ADR 0011 decision 7 and the plan's fixed
  decisions (both amended).
- **Image addresses follow the blog's network scope (owner decision, 2026-10-14).** The blog category
  is the address category of the post's canonical URL host, resolved at fetch time. A public blog
  permits only public image addresses. A private-network blog also permits private-network addresses
  (so a blog on an internal network or Tailscale can index images from that network). A loopback blog
  also permits loopback addresses. Link-local (including cloud metadata), any-local and multicast
  addresses are fetched only for a blog whose own host is in the same non-routable category. The rule is checked for the image host and every redirect target.
  Fetching is also limited by the redirect limit, the 10 MiB cap, the content-type check, the pixel
  limits and the timeouts.
- **No new index decision or outcome.** A pending image is retried through the existing staleness
  check: the stored fingerprint covers only settled images, so the post stays stale until every image
  is settled (3.7).
- **Two kinds of failure.** `Rejected` is final for that content (settled, never refetched until the
  content or recipe changes); `Unavailable` and image-embedder failures are pending (retried). This
  keeps tracking pixels, WebP and dead links from re-embedding a post on every reconcile.
- **Text-only providers are byte-for-byte unchanged** in recipe and fingerprint, so phase 3 re-embeds
  only the `gemma2` index.
- **Images are mapped by both anti-corruption adapters.** If only `SharedCatalogPosts` mapped them (as
  the plan's scope line reads), events and reconciles would compute different fingerprints and the
  post would be re-embedded back and forth.
- **Index file format 1 is still read**, as having no images, following catalog Q9, instead of the
  quarantine that unknown formats get.
- **Skipped images are logged, not reported.** The reconcile report is unchanged.
- Image fetching runs on the catalog sync thread like the rest of indexing; no concurrency work until
  the HTTP transport (as for Q9).

Owner decisions on the phase 3 questions (all resolved 2026-10-14, owner):
- **Q-IMG 5 (resolved): `ImageSource` lives in the domain**, in `search.domain.image`, because
  `PostIndexer` (domain) calls it (ADR 0006; same reasoning as Q-IMG 3).
- **Q-IMG 6 (resolved): no media-CDN list and no host allow-list.** Images may come from any `https`
  host, because blogs may use image domains of their own. Scope is instead decided by the blog
  category rule above.
- **Q-IMG 7 (resolved): accepted.** A retry re-embeds the post's text as well as its images, so while
  an image stays unavailable (or the machine is offline) that post is re-embedded at every reconcile.
- **Q-IMG 8 (resolved): the fingerprint covers each settled image's URL and description**, so an
  alt-text or caption edit updates the snippet (by re-embedding the post).
- **Q-IMG 9 (resolved): accepted.** A 403, 404 or 410 settles the image for that content version.
- **Q-IMG 10 (resolved): DNS rebinding is an accepted residual risk**, with no connection pinning.
  Addresses are checked after resolving, but the HTTP client resolves again when it connects. The
  blog category rule bounds what a fetch may reach, and link-local addresses are permitted only for a
  blog whose own host is non-routable.

Owner decisions (all resolved 2026-10-02, owner):
- **Q1 (resolved): the catalog change is in this slice.** It adds `shared.query.CatalogPosts` / `CatalogPostState` and
  the catalog's `ListCatalogPosts` (AC-SRCH-31). The integration events are **not** refactored to carry a `CatalogPostState`.
- **Q2 (resolved): dates are `Pacific/Auckland` calendar days**, inclusive at both ends. The zone is the
  named constant `PublishedDateRange.ZONE`, not a parameter (glossary "Blog time zone"; AC-SRCH-27).
- **Q3 (resolved): the default limit is 10.** Values out of range are clamped to 1..20, not rejected (AC-SRCH-20).
- **Q4 (resolved): no minimum similarity.** BGE v1.5 scores cluster in about [0.6, 1], and the model card
  advises relative order rather than a cut-off. If needed later, a relative rule can be added as another `SearchResults` rule.
- **Q5 (resolved): the snippet stays the whole chunk in this slice.** Trimming it to about 60 words is a
  **slice 3** item (in the MCP result mapping), not part of this slice.
- **Q6 (resolved): queries over 1,000 characters are rejected** (`InvalidSearchQuery`, `TOO_LONG`; AC-SRCH-19).
  A normal query of that length plus the instruction fits in one 510-token partition.
- **Q7 (resolved): summary-only posts are excluded from the index.**
  - **Ownership:** `Completeness.decide(...)` owns the rule, reached only through `IndexDecision.forPost`. A
    `SUMMARY` post gives `IndexDecision.Exclude`, which removes any existing entry and never embeds.
  - **Paths covered:** publish (AC-SRCH-33), summary to full (AC-SRCH-34), full to summary (AC-SRCH-35),
    reconcile (AC-SRCH-36), and the rule itself (AC-SRCH-37).
  - **Results:** completeness was removed from `PostMetadata` and `PostMatch`, because every indexed post is `FULL`.
  - **Contract:** the events already carry `completeness`, and `CatalogPostState` includes it (section 5).
- **Q8 (resolved): a reconcile against an empty catalog empties the index**, with no suppression, because the catalog is
  local and authoritative (AC-SRCH-30).
- **Q9 (resolved): embedding stays on the catalog sync thread for now; revisit in slice 3.**
  - The estimate (unmeasured) is 10–50 ms per passage of about 400 tokens per core, so a full rebuild is roughly
    tens of seconds to about a minute, plus a one-off lazy model load.
  - Moving `CatalogEventListener` onto a single-thread executor would be an adapter-only change.
- **Q10 (resolved): chunks are cut by word and token counts** (section 3.4), not at sentence or paragraph boundaries.

Slice 3 follow-ups recorded here (all addressed by the slice 3 model, `docs/domain/app.md` and ADR 0007, pending owner approval):
- file persistence of the index;
- trimming snippets to about 60 words;
- revisiting embedding on the sync thread;
- the DJL cache directory in `Main`;
- the shadow jar size (ADR 0005).
