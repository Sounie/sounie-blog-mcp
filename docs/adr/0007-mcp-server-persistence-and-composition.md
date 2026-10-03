# 7. MCP server, file persistence and the composition root

Date: 2026-10-03

## Status
- **Decisions 3 and 4 (persistence, slice 3a): Accepted (2026-10-03, by the owner).**
- **Decisions 1, 2 and 5 to 8 (slice 3b): Proposed.** Trimmed to essentials for a single-user tool; deferred items are listed in
  `docs/domain/app.md` section 7. They await the owner's 3b checkpoint (section 9 there).

SDK facts are from MCP Java SDK v2.0.1 (researcher, 2026-10-03). Items still UNVERIFIED are named where they matter.

## Context
Catalog and search work in process, but nothing runs yet. The in-memory repositories mean every start re-syncs about 230 posts and
re-embeds 1–2k chunks. ADR 0006 created the composition root `nz.sounie.blogmcp.app` (with `app.mcp`), but left its contents
to slice 3. The goal is a single jar that Claude starts as a stdio MCP server. The owner split the work into **3a (persistence)**
and **3b (server, wiring, scheduler, jar)**.

## Decision

### Slice 3b (proposed)
1. **MCP server in `app.mcp`, on MCP Java SDK 2.0.1** (`io.modelcontextprotocol.sdk:mcp`, with `mcp-json-jackson3`).
   - **Server:**
     - `McpServer.sync(new StdioServerTransportProvider(McpJsonDefaults.getMapper(), in, out))`, using the constructor that takes our own streams;
     - `.serverInfo("blog-mcp", version)`, `.instructions(...)` and the tools capability;
     - `.validateToolInputs(false)`.
   - **Tools:** `search_posts` and `get_post`, thin `SyncToolSpecification` handlers over `SearchPosts` and `GetPost`.
     - Each is built with `Tool.builder(name, mapper, schemaJson)`, an output schema, and
       `ToolAnnotations(readOnly = true, destructive = false, idempotent = true, openWorld = false)`.
     - Both descriptions and the server instructions carry a data-not-instructions notice.
   - **Types in `app.mcp`:**
     - `ToolDefinitions` (names, schemas, descriptions, annotations);
     - `ToolArguments` and `SearchPostsArguments` (parsing);
     - `ToolOutcome` (sealed: `Answered`, `NotFound`, `Failed`);
     - `ToolFailures` (a rule table from exception to outcome, and the only `catch` in the tool path).
   - **Output:** `structuredContent` that matches the output schema, plus a text copy of the same JSON for clients on protocol
     2024-11-05. Recoverable failures use `isError = true`, and we never throw `McpError`.
   - **Input validation has one owner, `ToolArguments`.** The SDK's schema validation is switched off because it would reject an
     out-of-range `limit` that the domain clamps, and its failure format is UNVERIFIED.
   - **Concurrency:** the SDK may run handlers concurrently, also during a sync. No extra machinery is needed: `IndexedPost`
     snapshots are immutable, `OnnxEmbedder` has a model lock, the file repositories restore a fresh aggregate on every `find`
     (decision 3), and the handlers are stateless.
   - **Proposed ArchUnit rule:** only `nz.sounie.blogmcp.app.mcp..` may depend on `io.modelcontextprotocol..` or `reactor..`.
2. **Domain additions owned by their contexts, not by the tools:**
   - `search.domain.index.Snippet`: the first 60 words of the best chunk, then ` …` (AC-SRCH-39);
   - `catalog.domain.post.PostReference`: a URL or a post ID, with `GetPost.byReference`;
   - `catalog.domain.site.SyncInterval` (decision 6).

### Slice 3a (accepted)
3. **File persistence: one JSON file per aggregate, atomic writes, quarantine on corruption.**
   - **Layout** under the data directory (`BLOG_MCP_DATA`, default `~/.local/share/blog-mcp`):
     `catalog/posts/<site>/<key>.json`, `catalog/checkpoints/<site>.json`, `search/index/<site>/<key>.json` and `cache/djl/`.
   - **Adapters:** `FilePostRepository` and `FileSyncCheckpointRepository` (`catalog.adapter.out`, Jackson 3), and
     `FileVectorIndex` (`search.adapter.out`). Each loads every file at startup, then is write-through, with lookups served from memory.
   - **Each file repository owns its sub-directory** (`catalog/posts`, `catalog/checkpoints`, `search/index`), resolved from the
     data directory it is given. `FileVectorIndex.open(dataDirectory, modelId, recipe)` also takes the current model ID and
     `IndexRecipe`, which the composition root takes from `PostIndexer.recipe()`. The recipe is stored in each index file.
   - **File keys are case-safe:** `FileKey` uses only `[a-z0-9_%-]`, percent-encoding upper case and every other character as UTF-8 in lower-case hex, because macOS APFS is case-insensitive by default.
   - **A file's location must match its content ID** (3a review). A post, checkpoint or index file whose ID does not map to the
     `FileKey` path it was found at is unreadable: it is quarantined, and catalog health becomes `DAMAGED`. Duplicate IDs are
     therefore impossible.
   - **Every stored file carries `"format": 1`** (post, checkpoint and index).
   - **Index files:** a missing `modelId` is unreadable (quarantined), not an incompatible model (deleted).
   - **No shared mutable aggregates:** the catalog repositories keep immutable stored snapshots and restore a fresh `Post` or
     `SyncCheckpoint` on every `find`, because tool calls read concurrently with the sync.
   - **Index file:** a JSON file whose vectors are the base64 of 384 little-endian float32 values. That is bit-exact, about 2×
     smaller than JSON numbers, and keeps a post in **one** file, so it needs only one atomic move.
   - **Atomic write:**
     - write a temporary file **in the target's directory**, then `FileChannel.force`, then `Files.move(ATOMIC_MOVE)`;
     - leftover `*.tmp` files are deleted at load;
     - forcing the directory to disk is not attempted, because it is not portable;
     - with `ATOMIC_MOVE`, replacing an existing target is "implementation specific" in the JDK. On Linux and macOS it is
       `rename(2)`, which is UNVERIFIED from source, so a test proves replace-on-move on both platforms (AC-APP-41).
   - **Corruption:** an unreadable file is renamed to `.corrupt`, logged as one stderr line, and treated as absent. It never crashes the app.
     - A damaged catalog store makes the startup sync a forced `RECONCILE` (`StorageHealth`).
     - Damaged index entries are re-added by the reconcile.
   - **Recipes:** recipe changes after a restart are caught by the stored fingerprint (ADR 0005). An index file whose **model ID**
     differs from the current model is not loaded (its vector space is incompatible) and is re-embedded.
4. **`nz.sounie.blogmcp.shared.storage`: a new JDK-only package** used by both contexts' adapters instead of duplicating code:
   - `AtomicFile` writes atomically, renames unreadable files to `.corrupt`, and sweeps `.tmp` files;
   - `FileKey` names files;
   - `StoredFiles` (public) finds, sweeps and reads stored JSON files. An I/O error on one file quarantines or skips that file, and
     never aborts startup.

   A generic helper formats a quarantine log reason from an exception. This extends ADR 0006's description of `shared`, which was "published language", with one
   JDK-only infrastructure package. The existing rule that `shared` depends on no context still holds.

### Slice 3b (proposed), continued
5. **Composition root `app`:**
   - **Stdout:** `StdoutGuard` hands the real stdout to the transport and redirects `System.out` to stderr.
   - **Paths:** `AppPaths` resolves the config file and the data directory, which is created at startup.
   - **Configuration:** a missing or invalid configuration, or a data directory that cannot be created, means exit status 1 with
     one stderr message and no server.
   - **Native libraries** keep their default locations (DJL's `~/.djl.ai`, ONNX Runtime's temporary directory) and are released
     on JVM exit.
   - **Logging:** our code logs only to an injected stderr `PrintStream`. The SDK's SLF4J output goes to `slf4j-simple` 2.0.16 on
     stderr at `warn`, configured by a bundled `simplelogger.properties`.
   - **Exit:** `Main` waits for end of input, detected by `EndOfInputWatch` (our wrapper around the stdin given to the transport),
     then calls `closeGracefully()` and exits with status 0. It does not rely on the transport's threads to keep the JVM alive
     (UNVERIFIED). No drain or shutdown sequence is needed: every write is atomic and nothing is buffered (decision 3).
6. **Scheduling and the sync interval:**
   - **The job:** `SyncAndReconcile` runs `SyncAllSites.run(mode)` and then `ReconcileIndex.run()` on one thread, through our
     `JobTimer` port (`ExecutorJobTimer`, a single-thread scheduled executor), now and then with a **fixed delay**. Runs therefore never
     overlap, and the reconcile always follows its sync. Step failures are caught and logged, and never cancel the schedule.
   - **The interval:** it is configured as `"syncEveryHours"` (a whole number, at least 1, default 24) in the existing config file,
     which stays named `sites.json`.
     - It is owned by catalog: `SyncInterval` and `SyncIntervalSetting` in `catalog.domain.site`, beside `SitesConfiguration`. They are not in `sync`,
       because `sync` depends on `site` and the reverse would create a cycle.
     - It is parsed by the file's one parser, `JsonFileSiteDirectory`, and an invalid value is reported with every other
       configuration violation.
     - `app` reads `syncInterval()` at startup.
   - **The daily reconcile:** the catalog rule is unchanged (due when strictly older than 24 hours). With the default 24-hour fixed delay, every run is
     in practice a reconcile.
   - **The first run** uses `StorageHealth.startupSyncMode()`. The server answers from the persisted index immediately.
   - **Embedding stays on the job's thread** (closes search Q9).
7. **Packaging:**
   - The Shadow plugin `com.gradleup.shadow` 9.6.1 produces `blog-mcp-all.jar`, with `mergeServiceFiles()` and
     `duplicatesStrategy = INCLUDE`. That is required, because `McpJsonDefaults.getMapper()` finds the Jackson 3 mapper through `ServiceLoader`.
   - `META-INF/*.SF`, `*.DSA` and `*.RSA` are excluded explicitly.
   - The manifest has `Main-Class`, `Implementation-Version` and `Enable-Native-Access: ALL-UNNAMED` (JEP 472).
8. **Testing against the real SDK, as "don't mock what you don't own" requires:**
   - handler-level tests of our `SyncToolSpecification` handlers;
   - subprocess tests with the real `McpClient` and `StdioClientTransport` for `tools/list`, stdout cleanliness, startup
     failure and the jar round trip (including exit when stdin closes).

   The SDK has no in-memory transport, and we do not hand-write a JSON-RPC client.

## Consequences
- **New dependencies:**
  - `io.modelcontextprotocol.sdk:mcp` 2.0.1, which brings `reactor-core` 3.7.0, `slf4j-api` 2.0.16 and Jackson 2 `jackson-annotations` 2.21.
    Annotations are allowed; the `no_jackson_2` rule bans only `databind`/`core`.
  - `org.slf4j:slf4j-simple` 2.0.16 (runtime).
  - The Shadow plugin 9.6.1.
- **Jar size:** about 130 MB or more, mostly ONNX Runtime.
- **Restarts:** a restart costs one load of a few hundred small files (well under a second). A first-ever start, or a recipe or
  model change, still embeds everything in the background (tens of seconds), while the server answers from whatever is present.
- **Crash safety:** a crash can lose only the write in progress, never corrupt a stored file. A quarantined file means data loss
  only until the next reconcile.
- **Known limit: power loss.** The directory is not fsynced, so on Linux the latest rename may not survive a power loss. This is
  acceptable, because every store can be rebuilt (by sync and reconcile). A best-effort directory fsync is an optional follow-up.
- **Known limit: file-name length.** A `FileKey` can be up to 3× the ID's length, and file names are capped at about 255 bytes, so a
  source post ID longer than about 80 escaped characters cannot be saved. Today's IDs are short and numeric; a guard (e.g. hashing
  over-long keys) can be added later if needed.
- **ArchUnit additions:**
  - SDK and Reactor types only in `app.mcp`;
  - `shared.storage` uses JDK types only (covered by the existing `shared` rule).
- **Restart to apply configuration changes:** the tool schema's `site` enum and the sync interval are read at startup. Syncs still
  re-read the sites on every run.
- **Testing the jar:** the jar round-trip and subprocess tests are slower (`@Tag("model")` for the jar). Handler-level tests keep
  the tool logic fast.
- **Deferred, add if needed** (single-user tool): a data-directory lock against two servers, a warming-up note in search results,
  a shutdown drain, moving the DJL cache into the data directory, a schema-versus-parser validator test, jar slimming, directory
  fsync, and a guard for over-long file keys. See `docs/domain/app.md` section 7.
