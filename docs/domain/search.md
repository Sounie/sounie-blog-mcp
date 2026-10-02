# Search bounded context

Status: approved by the owner on 2026-10-02 (decisions recorded in section 9)
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
- turning each chunk, together with the post title, into an **embedding** with the local BGE-small model
  (nothing leaves the machine);
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
| Post ID | Search's own copy of the catalog's post identity, in the external form `<siteId>:<sourcePostId>`. Parsed from the published language, and never shared as a catalog type. | `PostId` (search.domain) |
| Site ID | The site part of a post ID (`[a-z0-9-]{1,40}`). It is used for the site filter. | `SiteId` (search.domain) |
| Completeness | Whether the catalog has the `FULL` post text or only a `SUMMARY`. It is also the **indexability rule**: `FULL` posts go through the normal index decision, and `SUMMARY` posts are always excluded (owner decision, Q7). It is not stored in the index and not shown in results, because everything indexed is `FULL`. | `Completeness` (enum with behaviour `decide(...)`) |
| Excluded (post) | A catalog post that must not be in the index because its completeness is `SUMMARY`. Excluding a post removes any entry it already has. | `IndexDecision.Exclude` |
| Post to index | The catalog's current state of one post, translated into search language: post ID, completeness, post metadata, title and body. This is the single input to every index decision, whether it came from an event or from a reconcile. | `PostToIndex` |
| Post metadata | Everything about an indexed post that search stores but does **not** embed: site ID, canonical URL, title, tags, published at and updated at. A difference in metadata alone never triggers re-embedding. | `PostMetadata` |
| Word sequence | The post body after **text normalisation**, as an ordered list of words. Any run of Unicode whitespace (spaces, tabs, newlines, no-break space U+00A0) separates words. Any **overlong word** is split into pieces. | `WordSequence` |
| Overlong word | A whitespace-free run longer than 64 characters, such as a URL, base64 data or minified code. It is split into consecutive pieces of at most 64 characters, which then count as separate words. | `WordSequence` (rule `MAX_WORD_CHARS = 64`) |
| Token | One unit of the embedding model's WordPiece vocabulary. One model pass takes at most **510 content tokens** (512 including `[CLS]` and `[SEP]`). | (model concept) |
| Partition | LangChain4j's handling of input over 510 content tokens. It does **not** truncate. It splits the input into pieces of at most 510 tokens, embeds each one, and averages the results weighted by length. Search keeps every passage within **one** partition, so this averaging never happens (3.4). | (LangChain4j behaviour, `OnnxBertBiEncoder.MAX_SEQUENCE_LENGTH = 510`) |
| Token counter | Port that says how many model tokens one word costs. It is additive over words (see 3.4). It is implemented by `BgeTokenCounter`, which loads the model's own tokenizer file (`bge-small-en-v1.5-q-tokenizer.json`, bundled in the LangChain4j jar) with DJL's `HuggingFaceTokenizer`. | `TokenCounter` (domain port), `BgeTokenCounter` (adapter.out) |
| Query instruction | The fixed prefix `Represent this sentence for searching relevant passages: `, which the BGE v1.5 model card recommends for short-query-to-passage retrieval. It is put in front of **queries only**, never passages (lead decision). | `QueryPassage.INSTRUCTION` |
| Query passage | The exact text embedded for a query: the query instruction followed by the query text. It is built from a `QueryText`, so the prefix is applied in exactly one place. | `QueryPassage` |
| Chunk | A contiguous window of the word sequence that is embedded as one unit. A chunk has a zero-based **chunk index** in body order, and its text is its words joined by single spaces. | `Chunk` |
| Chunking policy | The rule that decides chunk boundaries and overlap: a target of 300 words, at most 400 body tokens per chunk, and an overlap of up to 50 words and 100 tokens. | `ChunkingPolicy` |
| Overlap | The trailing words of one chunk that are repeated at the start of the next, so that a sentence crossing a boundary appears whole in at least one chunk. | `ChunkingPolicy` |
| Passage | The exact text sent to the model for one chunk: the **title line**, a newline, and then the chunk text. If the title is blank, the passage is the chunk text alone. | `Passage` |
| Title line | The normalised title, cut to the longest whole-word prefix that fits in 64 tokens. | `PassageComposition` |
| Passage composition | The rule that builds passages from a title and chunks. | `PassageComposition` |
| Embed | Turn a passage or a query passage into an embedding using the local model. | `PassageEmbedder.embedPassages(...)`, `QueryEmbedder.embedQuery(QueryPassage)` |
| Embedder | The local embedding model, seen through two ports split by consumer (ADR 0006): the **passage embedder** (`modelId()` and `embedPassages(List<Passage>)`, used by indexing) and the **query embedder** (`embedQuery(QueryPassage)`, used by search). The only production implementation of both is `OnnxEmbedder`, the only class that touches LangChain4j. | `PassageEmbedder` (`search.domain.embedding`), `QueryEmbedder` (`search.domain.query`) |
| Embedding | A vector of exactly 384 finite floats, normalised to unit length when it is created. The model already uses CLS pooling with L2 normalisation, so in practice this is a defensive no-op. | `Embedding` |
| Similarity | The cosine similarity of two embeddings. Because embeddings are unit length, this is their dot product. Its range is [-1, 1], and higher means more related. | `Similarity` |
| Index recipe | The identity of the method used to build an index entry: model ID, chunking policy parameters and passage composition version, e.g. `bge-small-en-v1.5-q/w300-t400-o50-oc100-c64/tt64-p1` (model ID; target words, body tokens, overlap words, overlap token cap, maximum word characters; title tokens and composition version). Changing any part makes every entry stale. | `IndexRecipe` |
| Content fingerprint | SHA-256 over the index recipe, the normalised title and the normalised body. If two posts have the same fingerprint, they produce the same passages. Tags and other metadata are **not** included. | `ContentFingerprint` |
| Indexed post | The aggregate: one post's metadata, its content fingerprint and its indexed chunks. | `IndexedPost` |
| Indexed chunk | A chunk's index and text together with its embedding. | `IndexedChunk` |
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
| Chunk hit | The similarity of one indexed chunk to the query. | `ChunkHit` |
| Post match | One post in the results: its post metadata, its **best chunk hit**'s similarity as the post's score, and that chunk's text as the **snippet**. | `PostMatch` |
| Best chunk | The chunk hit of a post with the highest similarity. If two are equal, the lower chunk index wins. | `IndexedPost.bestMatch(Embedding)` |
| Snippet | The text of the best chunk, without the title line. It may be empty for a post with an empty body. In this slice it is the whole chunk. Trimming it to about 60 words is a **slice 3** item (Q5). | `PostMatch.snippet()` |
| Search results | The post matches ordered by **ranking** and cut to the limit. | `SearchResults` |
| Ranking | Order by score, highest first. Ties go to the more recent `publishedAt` first, and then to the lower post ID (compared as a string). | `PostMatch.RANKING` |
| Search posts | Answer a search query. | `SearchPosts` use case |

## 3. Aggregates and value objects

### 3.1 `IndexedPost` (aggregate root, one per post ID)

State: `PostId id`, `PostMetadata metadata`, `ContentFingerprint fingerprint`, `List<IndexedChunk> chunks`.

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
  chunk, picks the best chunk (ties go to the lower chunk index), and returns empty only when the post
  has no chunks.

Created only by the domain service `PostIndexer.index(PostToIndex)`. This service builds the
`WordSequence`, applies the `ChunkingPolicy`, composes the passages, calls `PassageEmbedder.embedPassages`
once for all of the post's passages, and then constructs the aggregate. The embedder is called
**before** anything in the index changes ("embed before mutate").

Invariants:
1. **Identity**: the post ID is fixed. The site ID in the metadata equals the site part of the post ID.
2. **Chunks**: the chunk indexes are exactly `0..n-1`, in body order. A post has zero chunks
   **only** when both its normalised title and its normalised body are empty. Such a post is still recorded,
   so that reconciles do not keep re-adding it, but it never matches a query.
3. **Embeddings**: one per chunk, each 384-dimensional and unit length, all made under the same index
   recipe as the fingerprint.
4. **Fingerprint integrity**: the fingerprint is the one computed from the content the chunks were built
   from. `withMetadata` cannot change content, and new content always goes through `PostIndexer`.
5. **Whole replacement**: an entry is saved and replaced as a whole. A search never sees a mixture of
   old and new chunks of one post (a contract of the `VectorIndex` port).
6. **Only full posts**: an indexed post was always built from a `FULL` post to index. A `SUMMARY` post
   never reaches `PostIndexer`, because `Completeness.SUMMARY` turns it into `Exclude` first.

### 3.2 `VectorIndex` (domain port; the repository of `IndexedPost`)

`Optional<IndexedPost> find(PostId)`, `void save(IndexedPost)` (atomic replace),
`boolean remove(PostId)` (returns whether something was removed), `Stream<IndexedPost> all()`
(a consistent snapshot per post), `Set<PostId> ids()`.

`InMemoryVectorIndex` (in `search.adapter.out`, production code) keeps a concurrent map keyed by post ID.
**File persistence is out of scope for this slice and belongs to slice 3**, next to the catalog's
file repositories. Until then, a restart starts with an empty index and the startup reconcile rebuilds it.

Search is a brute-force scan: about 1–2k chunks × 384 dimensions per query, which is well under a
millisecond of arithmetic. No approximate-nearest-neighbour structure is needed (ADR 0005).

### 3.3 Value objects and the type that owns each decision

| Decision | Owner | Notes |
|---|---|---|
| Parse post ID and site ID; reject site mismatch and unknown completeness | `PostId`, `SiteId`, `Completeness`, `PostToIndex.of(...)` (a static factory taking JDK types, called by both adapters) | It throws `MalformedCatalogPost` for an invalid published-language payload. |
| Text normalisation: whitespace runs (incl. U+00A0, tabs, newlines) separate words, and leading or trailing whitespace is dropped | `WordSequence` | The title uses the same rule. |
| Splitting overlong words into pieces of at most 64 characters | `WordSequence` | Joining the pieces with no separator gives back the original word. In a snippet the pieces appear separated by spaces. |
| Chunk boundaries and overlap | `ChunkingPolicy` | See 3.4. |
| Token cost of a word | `TokenCounter` port (adapter: `BgeTokenCounter` over DJL `HuggingFaceTokenizer` and the bundled tokenizer file; tests: deterministic fake) | This is the model's exact tokenizer, so no estimate is needed. |
| Passage = title line + newline + chunk text; title cut to 64 tokens; blank title omitted | `PassageComposition` | |
| Whether a post may be indexed at all | `Completeness.decide(...)` (`FULL` delegates to the normal decision; `SUMMARY` always gives `Exclude`) | Owner decision, Q7. It is reached only through `IndexDecision.forPost`, so events and reconciles share it. |
| What changes re-embedding | `ContentFingerprint.of(IndexRecipe, title, WordSequence)` | Tags, URL and dates are excluded. Completeness is decided before the fingerprint matters. |
| Recipe identity | `IndexRecipe` | Derived from the `ChunkingPolicy` parameters, the composition version and `PassageEmbedder.modelId()`. |
| Dimension check, finiteness, non-zero norm, normalisation, cosine | `Embedding` | It throws `InvalidEmbedding`. |
| Query instruction prefix (queries only) | `QueryPassage.of(QueryText)` | It is a domain value, so the asymmetry is testable without the model (AC-SRCH-32). Queries and passages go through separate ports, `QueryEmbedder.embedQuery(QueryPassage)` and `PassageEmbedder.embedPassages(List<Passage>)`, so there is no flag parameter, and a passage can never get the prefix by type. The prefix is not part of `IndexRecipe`, because it never affects stored passages. |
| Upsert vs remove per event kind | `CatalogEventTranslation` (adapter.in) maps each event type to an `IndexChange` variant; `IndexChange.applyTo(IndexWork)` is polymorphic | Publish and revise **both** become `Upsert`, so duplicate or missed deliveries heal themselves. The pattern switch has one delegating arm per event type. |
| Add / re-embed / refresh / keep / exclude / remove | `IndexDecision` (sealed), produced by the single entry point `IndexDecision.forPost` (for upserts) or `IndexDecision.remove(PostId)` (for withdrawals and orphans). Each variant has `apply(IndexWork)` returning an `IndexOutcome`. | The same type is used by `IndexPost` and `ReconcileIndex`. |
| Reconcile plan (missing, stale, metadata, summary-only, malformed, orphan) | `ReconcilePlan.between(List<CatalogEntry> catalog, VectorIndex snapshot, IndexRecipe)` | It produces a list of `IndexDecision`s and an orphan-removal verdict. Each variant contributes through its own method (polymorphism on `CatalogEntry`, not an if-chain):<br>• `Readable` goes through `IndexDecision.forPost`;<br>• `Unreadable` gives `IndexDecision.Unreadable(PostId)`, which never touches the index and reports `FAILED` with the reason. Its ID counts as current;<br>• `Unidentified` contributes no decision, but suppresses all orphan removals in the run.<br>A post ID listed more than once in the catalog becomes **one** `IndexDecision.Unreadable` whose reason mentions "duplicate". Its existing entry stays, it is reported `FAILED`, and the other posts are still planned: a duplicate never aborts the reconcile. A summary-only or malformed catalog post is *not* an orphan. The plan is pure and tested without an embedder. |
| Blank or overlong query | `QueryText` | It throws `InvalidSearchQuery` with reason `BLANK` or `TOO_LONG`. |
| Limit clamping and default | `ResultLimit.of(int)`, `ResultLimit.defaultLimit()` | There are no Optional parameters; the MCP adapter picks the factory. |
| Date-range validity and inclusion | `PublishedDateRange` (factories `unbounded()`, `from(d)`, `to(d)`, `between(a, b)`; `includes(Instant)`; constant `ZONE = Pacific/Auckland`) | It throws `InvalidSearchQuery` with reason `FROM_AFTER_TO`. `includes` converts the instant to a `Pacific/Auckland` calendar date and compares inclusively. The zone is fixed, never passed in, so DST is handled by `java.time` rules. |
| Site inclusion | `SiteFilter` (`AnySite`, `OnlySite`) with `includes(SiteId)` | |
| Composition of filters | `SearchFilters` (a list of `Predicate<PostMetadata>`; all must hold) | It is a rule table, so adding a tag filter later means adding a rule. |
| Best chunk and snippet | `IndexedPost.bestMatch` using `ChunkHit.BEST_FIRST` (similarity descending, then chunk index ascending) | |
| Grouping, ranking and tie-breaks, then limit | `SearchResults.rank(Stream<PostMatch>, ResultLimit)` with `PostMatch.RANKING` | Grouping is structural: there is one `bestMatch` per indexed post. |

### 3.4 Chunking policy (numbers and why)

Parameters: `TARGET_WORDS = 300`, `BODY_TOKEN_BUDGET = 400`, `OVERLAP_WORDS = 50`,
`OVERLAP_TOKEN_CAP = 100`, `MAX_WORD_CHARS = 64`; in `PassageComposition`, `TITLE_TOKEN_BUDGET = 64`.

Algorithm (deterministic):
1. A chunk starts at word `s` and grows one word at a time. It **closes before** a word that would make it
   exceed 300 words or 400 body tokens.
2. The next chunk starts at the longest suffix of the previous chunk that has at most 50 words **and**
   at most 100 tokens, but always at least one word after the previous start, so every chunk adds at least one new word.
   The overlap also leaves room in the 400-token budget for the next word. With words of at most 64 tokens this never
   binds, but after an unusually expensive word it shrinks the overlap instead of producing a chunk that is only overlap.
3. Chunking stops when the chunk just closed contains the last word. No chunk consists only of overlap.
4. An empty word sequence gives **one** chunk with empty text, so the passage is the title alone.
   If the title is also empty, the result is **zero** chunks.

Why these numbers:
- **300 words** is the size the plan asked for. It is about one topical section of a blog post: big enough to carry
  meaning, and small enough that one strong paragraph is not diluted by a whole post. English prose
  costs about 1.3 WordPiece tokens per word, so about 390 tokens.
- **400 body tokens** is the hard guarantee. Technical posts contain code, identifiers and punctuation that cost
  2–3 tokens per "word", so a word count alone cannot keep a chunk within the model's input.
  LangChain4j does not truncate long input. It splits anything over **510 content tokens** into
  partitions and averages their embeddings weighted by length, which would blur a chunk's meaning. So
  the goal is that **every passage fits in a single partition**. The worst case is 64 (title) + 400 (body) +
  1 (the newline adds no WordPiece token, but is counted conservatively) = **465 content tokens ≤ 510**
  (467 ≤ 512 with `[CLS]`/`[SEP]`). That leaves a margin of 45 tokens for any non-additivity in the tokenizer.
- **50 words of overlap (about 17%)** is two or three sentences. The 100-token cap stops a code-heavy overlap
  from eating the next chunk's budget. With overlap ≤ 100 tokens and any single (split) word far below
  the remaining 300 tokens, every chunk can always add a new word, so chunking always terminates.
- **64-character pieces**: the budgets are enforced by exact counting with the model's tokenizer
  (`BgeTokenCounter`), not by a characters-per-token bound. A "one token per character" bound would not
  hold anyway: the tokenizer's NFD normalisation can expand one Hangul syllable into 2–3 jamo. The
  64-character pieces keep any single word far below the 400-token body budget, so a single word can
  never fill a chunk on its own. AC-SRCH-8's input includes a Hangul fragment for this reason.
- Expected volume: 229 posts, mostly 300–1,500 words, gives roughly 700–1,500 chunks.

How "one partition" is ensured:
- LangChain4j keeps its token methods package-private, so `BgeTokenCounter` loads the same
  `bge-small-en-v1.5-q-tokenizer.json` that the model uses, through DJL's `HuggingFaceTokenizer`. DJL is
  already a transitive dependency, so this is the exact tokenizer and not an estimate.
- BERT's pre-tokenizer splits on whitespace and punctuation before WordPiece, so the cost of a text is
  the sum of the costs of its words (additive). The budget is checked word by word while chunking.
- AC-SRCH-8 checks the result against the real tokenizer.

Queries: a query passage is the query instruction (about 10 tokens) plus at most 1,000 characters of
query text. 1,000 characters cost at most about 1,000 tokens in the worst case (one per character), but
fewer than 300 for normal text. An extreme query could therefore be partitioned. That is acceptable for
a query, and Q6 bears on it.

### 3.5 Ranking choice

A post's score is its **best chunk's** similarity (max-pooling).
- A sum or count of chunk hits would favour long posts.
- A mean would punish a long post with one highly relevant section.

The best chunk is also the natural snippet, so the score and the snippet always agree. Ties are rare
with floats but must be deterministic: newer `publishedAt` first, then post ID ascending.
No minimum-similarity threshold is applied (Q4). BGE v1.5 similarities cluster in about [0.6, 1].
The model card warns that a score above 0.5 does not mean "similar" and recommends relying on relative
order rather than an absolute cut-off, which is exactly what ranking does.

## 4. Domain events

### 4.1 Consumed (published language in `nz.sounie.blogmcp.shared.event`)

| Integration event | Translated to | Effect |
|---|---|---|
| `CatalogPostPublished` | `IndexChange.Upsert(PostToIndex)` | A `SUMMARY` post is `Exclude`, so nothing is indexed. A `FULL` post is `Add` if absent. If already present (duplicate delivery, or the reconcile got there first), the fingerprint decides, usually `Keep`. |
| `CatalogPostRevised` | `IndexChange.Upsert(PostToIndex)` | The completeness is checked first. A revision to `SUMMARY` is `Exclude`, which removes any entry. A revision from `SUMMARY` to `FULL` finds no entry, so it is `Add`. For a `FULL` post that is already indexed, the fingerprint decides between `ReEmbed`, `RefreshMetadata` and `Keep`. The event's `changed` set is **not** used for the decision. The fingerprint is authoritative and also works for reconciles, which have no `changed` set. A tags-only, URL-only or published-at-only revision therefore causes **no re-embedding**. A revision for a `FULL` post that is not indexed (a missed publish) is `Add`. |
| `CatalogPostWithdrawn` | `IndexChange.Remove(PostId)` | `REMOVED`, or `ALREADY_ABSENT` (idempotent). The reason is ignored. |

`CatalogEventListener` (in `search.adapter.in`) subscribes to the in-process bus. The bus is
synchronous, so indexing runs on the catalog's sync thread. The listener **isolates failures**: any
exception from translation or indexing is logged to stderr (stdout is reserved for the MCP stdio
protocol in slice 3) and swallowed. That way a search failure can never abort a catalog sync, and the next reconcile
repairs the entry.

Index mutations (`IndexPost` and `ReconcileIndex`) are serialised by a single lock in search's
application layer, so that a decision and its save cannot interleave with another mutation of the same post.
Searches do not take that lock.

**Model access.** LangChain4j shares one ONNX session and one tokenizer per model, and its own
`embedAll` already calls them from a thread pool sized to `availableProcessors()`. That suggests
concurrent use is intended, but nothing documents it. Because searches do not take the index lock, a
query embedding can run at the same time as an indexing embedding. **Recommendation (adopted in ADR 0005):**
`OnnxEmbedder` serialises its own public calls (`embedPassages` for one post, `embedQuery`) with one
lock, rather than relying on that undocumented assumption.
- The cost is bounded. One post's passages take about 0.1–0.5 s (an estimate). A query waits at most
  that long, even during a reconcile, because reconciles call the embedder once per post.
- LangChain4j's parallelism *within* one `embedAll` call is left alone, because that is its own
  exercised path.

### 4.2 Published

None. No other context needs to know about indexing. Outcomes are returned as results
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
- **search to LangChain4j / ONNX Runtime / DJL (conformist, wrapped)**: two classes in `search.adapter.out`:
  - `OnnxEmbedder` implements both `PassageEmbedder` and `QueryEmbedder`. It is the only class that imports `dev.langchain4j..`, which
    also brings Jackson 2 in transitively (our code stays on Jackson 3; ArchUnit `no_jackson_2`).
  - `BgeTokenCounter` implements `TokenCounter`. It imports only `ai.djl.huggingface.tokenizers..`, and
    reads the tokenizer file as a classpath resource.

  The model and tokenizer files are bundled in the LangChain4j jar, so nothing is downloaded at runtime.
  The model loads lazily on first use, so the first embedding call is slow.
- **search to mcp (slice 3)**: the `search_posts` tool calls `SearchPosts` and maps its arguments with
  `SearchQuery`'s factories (`ResultLimit.defaultLimit()` when `limit` is absent,
  `PublishedDateRange.unbounded()` when both dates are absent, and so on).

### 6.1 Domain sub-packages (ADR 0006)

`search.domain` is split by concept into sub-packages with acyclic dependencies. ArchUnit's
`domain_sub_packages_are_free_of_cycles` enforces this.

| Sub-package | Depends on | Types |
|---|---|---|
| `post` | (nothing) | `PostId`, `SiteId`, `PostMetadata`, `MalformedCatalogPost` |
| `text` | (nothing) | `WordSequence`, `WordCosts`, `ChunkingPolicy`, `Chunk`, `Passage`, `PassageComposition`, `TokenCounter` (port) |
| `embedding` | `text` | `Embedding`, `Similarity`, `InvalidEmbedding`, `EmbedderUnavailable`, `PassageEmbedder` (port) |
| `index` | `embedding`, `post`, `text` | `IndexedPost`, `IndexedChunk`, `PostIndexer`, `PostToIndex`, `Completeness`, `PostMatch`, `ChunkHit`, `ContentFingerprint`, `IndexRecipe`, `IndexChange`, `IndexDecision`, `IndexOutcome`, `IndexWork`, `InvalidIndexedPost`, `VectorIndex` (port) |
| `query` | `index`, `embedding`, `post`, `text` | `SearchQuery`, `QueryText`, `QueryPassage`, `InvalidSearchQuery`, `ResultLimit`, `PublishedDateRange`, `SiteFilter`, `SearchFilters`, `SearchResults` |
| `reconcile` | `index`, `post` | `CatalogEntry`, `ReconcilePlan` |

Nothing in the domain depends on `query`. `PostToIndex`, `Completeness` and `PostMatch` live in `index`,
not in `post` or `query`, to keep this graph acyclic.
Outside the domain:
- `PostCatalog` and `QueryEmbedder` (ports used only by use cases), `ReconcileReport` and the use cases live in `search.application`;
- `OnnxEmbedder`, `BgeTokenCounter`, `InMemoryVectorIndex` and `SharedCatalogPosts` live in `search.adapter.out`;
- `CatalogEventListener` and `CatalogEventTranslation` live in `search.adapter.in`.

## 7. Application use cases (`search.application`)

- `IndexPost.apply(IndexChange)` returns an `IndexOutcome`. It reads as a straight line: decide, then apply the decision.
- `SearchPosts.search(SearchQuery)` returns `SearchResults`: `embedQuery(QueryPassage.of(query.text()))`, then `index.all()`, filtered
  by `SearchFilters`, then `bestMatch` for each post, then `SearchResults.rank(limit)`.
  A `PostMatch` carries the post ID, site ID, canonical URL, title, tags, published at, updated at, score and
  snippet. It has **no completeness**, because every indexed post is `FULL`.
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

**AC-SRCH-3: The token budget closes a chunk early, and the overlap is token-capped.**
Given a fake `TokenCounter` that costs 2 tokens per word, and a body of 500 words,
When it is chunked,
Then the first chunk is `w1..w200` (400 tokens), and the next one starts at `w151` (50 words = 100 tokens).
And given a counter costing 4 tokens per word, the overlap is 25 words (the 100-token cap), not 50.
And with any counter, no chunk exceeds 400 body tokens, and every chunk contains at least one word not in the previous chunk.

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

**AC-SRCH-7: The title is part of every passage.**
Given a post titled `Records in Java 25` with a 700-word body,
When its passages are composed,
Then each of the 3 passages is `Records in Java 25`, a newline, and then that chunk's text.
And given a title longer than 64 tokens, the title line is its longest whole-word prefix of at most 64 tokens.
And given a blank title, the passage is the chunk text alone.

**AC-SRCH-8: Every passage fits in one model partition (adapter test, real tokenizer).**
Given a pathological body of 3,000 "words" mixing Java code, long identifiers, punctuation runs, URLs, a 2,000-character string with no whitespace, and a run of Hangul syllables with no whitespace (which NFD normalisation expands into jamo, so it costs more than one token per character), and a 120-word title,
When it is chunked and composed with the real `BgeTokenCounter` as `TokenCounter`,
Then every passage, encoded as a whole by the model's tokenizer (`bge-small-en-v1.5-q-tokenizer.json` through DJL), has at most 465 content tokens. That is within LangChain4j's single-partition limit of 510, so no passage is split and averaged.
(The test JVM must point DJL's cache at a writable directory; see ADR 0005.)

### Embedding

**AC-SRCH-9: The `Embedding` value object.**
Given a vector of length 383 or 385, one containing `NaN` or infinity, or the all-zero vector,
When an `Embedding` is created,
Then `InvalidEmbedding` is raised.
And given a valid 384-dimensional vector of length 3.0, the embedding has unit length (to within 1e-6), its similarity with itself is 1.0, and its similarity with an orthogonal embedding is 0.0.

**AC-SRCH-10: The real model ranks related text above unrelated text (one adapter test).**
Given the real `OnnxEmbedder`, the query `how do I configure a Gradle build for Java`, a related passage about writing a `build.gradle.kts` for a Java project, and an unrelated passage about baking sourdough bread,
When the query is embedded as a `QueryPassage` (with the instruction) and both passages are embedded as passages,
Then all embeddings have dimension 384 and unit length, and the related passage's similarity to the query is strictly greater than the unrelated one's. Only the relative order is asserted, never an absolute score (BGE scores cluster in about [0.6, 1]).

**AC-SRCH-32: Only queries carry the query instruction.**
Given the query text `records in java`,
When a `SearchQuery` is searched,
Then the `QueryEmbedder` receives exactly `Represent this sentence for searching relevant passages: records in java` as its query passage.
And when a post is indexed, no passage given to `PassageEmbedder.embedPassages` starts with the instruction.
And changing the instruction does not change any `IndexRecipe` or `ContentFingerprint`.

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

## 9. Decisions

Model decisions (approved with the model by the owner on 2026-10-02):
- Tags are **not embedded**. They are metadata only, so a tags-only revision does not re-embed (AC-SRCH-14).
  Adding them to every passage would bias every chunk towards the tag words and couple tag edits to
  re-embedding.
- A re-index is decided by the **content fingerprint**, not the event's `changed` set, so events and reconciles share one decision path.
- A post is ranked by its best chunk (max-pooling), and the snippet is that chunk.
- Publish and revise are both upserts. Indexing is idempotent.
- `IndexPost` embeds before it changes the index, so a failure keeps the old entry. The listener swallows and logs failures.
- Queries get the BGE query instruction and passages do not (lead decision, 2026-10-02; AC-SRCH-32).
- A malformed catalog post never causes an index removal during a reconcile (lead decision, review loop 1, B1; AC-SRCH-38):
  - one with a readable ID is `IndexDecision.Unreadable`: its entry is kept and it is reported `FAILED` with its reason;
  - one with an unreadable ID suppresses every orphan removal in that run (`ORPHAN_REMOVAL_SUPPRESSED`).
- Every passage fits in one model partition (at most 465 of 510 content tokens), counted with the model's exact tokenizer (AC-SRCH-8).
- `OnnxEmbedder` serialises model access with one lock (section 4.1).

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

Slice 3 follow-ups recorded here:
- file persistence of the index;
- trimming snippets to about 60 words;
- revisiting embedding on the sync thread;
- the DJL cache directory in `Main`;
- the shadow jar size (ADR 0005).
