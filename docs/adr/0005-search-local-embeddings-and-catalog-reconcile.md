# 5. Search context: local embeddings, in-memory vector index and reconciling with the catalog

Date: 2026-10-02

## Status
Accepted (2026-10-02, by the owner, together with `docs/domain/search.md`)

## Context
Slice 2 adds the `search` context (already split from `catalog` by ADR 0003). It must turn posts into
a semantic index and answer queries:
- entirely on the local machine, with no API key and no network calls;
- without depending on `catalog` (ArchUnit `search_does_not_depend_on_catalog`).

The catalog publishes integration events in `shared`, but delivery is in-process, synchronous and
at most once, with no outbox. So search can miss events: a crash between save and publish, a failure
in a handler, or simply a restart while the index is in memory. catalog.md left this open as Q8.

Facts below about the libraries were verified on 2026-10-02 by the researcher and by the lead (Maven
Central, jar listings).

## Decision
1. **Embedding model**: BGE-small-en-v1.5, quantised ONNX, through
   `dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q` **1.20.2-beta30**, with dimension 384.
   - The model uses CLS pooling with L2 normalisation (as specified by BAAI), so cosine is a dot
     product. `Embedding` still normalises defensively.
   - The ONNX model (34 MB) and `bge-small-en-v1.5-q-tokenizer.json` are bundled in the jar and loaded from
     the classpath, so no network is used at runtime. The model loads lazily on first use.
   - Only two classes touch these libraries, both in `search.adapter.out`:
     - `OnnxEmbedder` implements `Embedder`, and is the only class that imports `dev.langchain4j..`.
     - `BgeTokenCounter` implements `TokenCounter`, and imports only DJL's
       `ai.djl.huggingface.tokenizers..`.
   - The proposal adds an ArchUnit rule: "only `nz.sounie.blogmcp.search.adapter.out..` may depend on
     `dev.langchain4j..`, `ai.onnxruntime..` or `ai.djl..`".
   - Jackson 2 arrives transitively. The existing `no_jackson_2` rule keeps our code on Jackson 3.
2. **Every passage fits in one model partition, guaranteed by construction.**
   - **How LangChain4j handles long input:** one pass takes at most 510 content tokens
     (`OnnxBertBiEncoder.MAX_SEQUENCE_LENGTH`, 512 with `[CLS]`/`[SEP]`, not configurable). Longer input
     is not truncated. It is split into partitions of at most 510 tokens, each partition is embedded,
     and the results are averaged weighted by length. Averaging would blur a chunk, so search keeps
     every passage within one partition.
   - **Budget:** chunking counts tokens word by word through `TokenCounter`, relying on BERT
     pre-tokenisation being additive over words. A chunk's body is capped at 400 tokens and the title line at 64,
     so the worst case is 465 content tokens, within 510 and with a 45-token margin. Overlong words are split into
     pieces of at most 64 characters.
   - **Exact tokenizer, no fallback:** LangChain4j's token methods are package-private, so
     `BgeTokenCounter` loads the bundled `bge-small-en-v1.5-q-tokenizer.json` with DJL's
     `HuggingFaceTokenizer` (DJL 0.36.0, already a transitive dependency). This is the tokenizer the model uses.
3. **Query instruction, on queries only** (lead decision). The domain value `QueryPassage` puts the
   BGE v1.5 model card's recommended prefix `Represent this sentence for searching relevant passages: ` in front of
   query text. LangChain4j adds nothing itself. Passages are never prefixed (asymmetric retrieval,
   as the card recommends), so the prefix is not part of the `IndexRecipe` and changing it never
   re-embeds the index.
4. **Brute-force cosine** over all chunks. About 1–2k vectors of 384 floats needs no ANN library. Results
   are ranked by relative order, with no absolute threshold: BGE v1.5 scores cluster in about [0.6, 1], and
   the model card advises against fixed cut-offs.
5. **`VectorIndex` port with an in-memory implementation only in this slice.**
   - File persistence of the index is deferred to **slice 3**, alongside the catalog's file repositories
     and under the same data directory and atomic-write rules (ADR 0003 item 4).
   - Until then, every start rebuilds the index through a reconcile.
6. **Resolution of catalog.md Q8: a pull-based query contract in `shared`, plus fingerprint reconcile.**
   - `nz.sounie.blogmcp.shared.query.CatalogPosts` (`List<CatalogPostState> currentPosts()`, JDK types
     only, sorted by post ID) is implemented by the catalog in `catalog.application` (`ListCatalogPosts`).
   - Search consumes it only through its own port `PostCatalog`, implemented by
     `search.adapter.out.SharedCatalogPosts`, which translates into search's `PostToIndex`.
   - `ReconcileIndex` compares each catalog post's **content fingerprint** (SHA-256 of the index recipe,
     normalised title and normalised body), which search computes itself, and its metadata with the index:
     - a missing post is added;
     - a fingerprint mismatch is re-embedded (content changed, or a new recipe such as a model or chunking change);
     - a metadata-only difference is refreshed without embedding;
     - an orphan is removed.
   - The same `IndexDecision` type drives event handling, so events and reconciles cannot disagree.
   - It runs at startup, and after each scheduled `SyncAllSites` in the same job (slice 3 wiring).
     So it never reads a catalog that is being synced.
   - Rejected alternatives:
     - **Event replay** (the catalog re-publishes `CatalogPostPublished` for every post): it cannot reveal
       orphans without an extra "replay complete" marker, and it broadcasts to every subscriber.
     - **Outbox or durable log**: it guarantees delivery, but persistence and offsets are disproportionate
       for one process and about 230 posts. The reconcile is needed anyway for recipe changes.
     - **Catalog-computed fingerprints**: these would couple the catalog to search's embedding recipe.
     - **Comparing `updatedAt` only**: the catalog moves `updatedAt` without an event (`Touched`), and
       a recipe change has no timestamp.
7. **Failure isolation and concurrency.**
   - **Listener:** `search.adapter.in.CatalogEventListener` catches, logs (to stderr) and swallows every
     failure, because the bus is synchronous and a search failure must never abort a catalog sync.
   - **Embed before mutate:** `IndexPost` embeds before it changes the index, so a failed re-embed keeps
     the previous entry. The next reconcile repairs it.
   - **Index lock:** index mutations are serialised by one lock in `search.application`. Searches do not
     take it.
   - **Model lock:** LangChain4j shares one `OrtSession` and one tokenizer per model, and its `embedAll`
     already uses them from a thread pool (sized to `availableProcessors()`, or a custom `Executor`).
     Concurrent use therefore looks intended, but it is undocumented. Because a query can embed while
     indexing embeds, `OnnxEmbedder` **serialises its public calls with one lock**. A query waits at
     most for one post's passages (an estimated 0.1–0.5 s). LangChain4j's parallelism within one call
     is left alone.

8. **Owner decisions recorded with this ADR** (search.md section 9; all 2026-10-02):
   - **Summary-only posts are excluded from the index (Q7).**
     - `Completeness` (search's enum) owns the rule. It is reached only through `IndexDecision.forPost`, which events
       and reconciles share. `SUMMARY` gives `IndexDecision.Exclude`, which removes any entry and never embeds.
     - A revision from summary to full adds the post, and one from full to summary removes it.
     - The existing integration events already carry `completeness`, and `CatalogPostState` includes it, so
       the contract needs no further change.
     - Results no longer carry completeness.
   - **Published-date filters use `Pacific/Auckland` calendar days (Q2)**, inclusive at both ends, through the
     constant `PublishedDateRange.ZONE`.
   - **The catalog's `ListCatalogPosts` ships in this slice (Q1).**
   - **Accepted as recommended:**
     - default limit 10;
     - no minimum score;
     - queries over 1,000 characters rejected;
     - an empty catalog empties the index;
     - embedding on the catalog sync thread for now;
     - chunks cut by word and token counts.
   - **Deferred to slice 3:** trimming snippets to about 60 words, and revisiting embedding on the sync thread.

## Consequences
- **New dependency:** `dev.langchain4j:langchain4j-embeddings-bge-small-en-v15-q:1.20.2-beta30` goes into
  `gradle/libs.versions.toml` as `implementation`.
  - Transitively it brings `langchain4j-core` 1.20.2, `com.microsoft.onnxruntime:onnxruntime` 1.20.0,
    `ai.djl:api` 0.36.0, `ai.djl.huggingface:tokenizers` 0.36.0 and Jackson 2.
  - That is **about 130 MB** of jars. ONNX Runtime alone is 89 MB, much of it native debug symbols. This
    matters for the size of the slice 3 shadow jar, which may want to exclude unused native platforms.
  - Bundled natives: ONNX Runtime ships `osx-aarch64` and `linux-x64`, and DJL tokenizers ships
    `osx-aarch64` and `linux-x86_64`. This covers the owner's Mac and CI.
- **Native extraction (operational):**
  - DJL extracts its native `libtokenizers` to a cache directory, by default `~/.djl.ai`. The Claude Code
    sandbox cannot write there.
  - The DJL cache location (the `DJL_CACHE_DIR` environment variable or its system-property
    equivalent) must point at a writable directory: in the Gradle `test` task configuration, and in
    slice 3's `Main` before any DJL class loads. The exact property name is for the implementer to confirm against DJL 0.36.0.
  - Both LangChain4j's embedder and `BgeTokenCounter` use DJL, so this is process-wide.
  - ONNX Runtime extracts to `java.io.tmpdir`, which `./gw` already points at `$TMPDIR`.
- **Catalog change:** a small, additive change in this slice, the `ListCatalogPosts` implementation of
  the `shared` contract (AC-SRCH-31). `shared` gains a `query` package and still depends on no context.
- **Rebuild cost:** the index is rebuilt on every start until slice 3 persists it. At an estimated
  (unmeasured) 10–50 ms per passage per core, 1–2k chunks take roughly tens of seconds to a minute,
  plus the lazy model load on first use.
- **Thread:** indexing runs on the catalog's sync thread, which makes the first sync slower. This can be moved to a worker
  later in the adapter, without changing the domain.
- **Recipe changes:** changing chunking parameters or the model needs no migration. The recipe change
  makes the next reconcile re-embed everything. Changing the query instruction needs no re-embedding.
