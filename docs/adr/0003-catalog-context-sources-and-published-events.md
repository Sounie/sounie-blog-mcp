# 3. Catalog context: blog sources, local storage and published events

Date: 2026-10-01

## Status
Accepted (2026-10-01, by the owner, together with `docs/domain/catalog.md`)

## Context
The blog MCP server needs a local, up-to-date copy of the owner's posts from two platforms: WordPress
(REST API) and Blogger (JSON feed). A later `search` context must react when posts change, without
depending on catalog internals. There is not yet any HTTP, JSON or HTML handling in the build. Neither
platform API reports deletions. The Blogger blog currently publishes only a *short* feed (summaries).

## Decision
1. **New bounded context `catalog`** (`nz.sounie.blogmcp.catalog`). It owns sites, posts and sync
   checkpoints. Its language (sites, platforms, checkpoints, withdrawal) differs from the language of
   search (chunks, embeddings, similarity), so search is a separate context.
2. **Published language in `nz.sounie.blogmcp.shared`.** Catalog domain events (`PostPublished`,
   `PostRevised`, `PostWithdrawn`) stay in `catalog.domain`. The catalog application layer maps
   them, after each post has been saved, to integration events in `shared`, which use only JDK types,
   and publishes them on a small in-process event bus that is also in `shared`. `shared` depends on no
   context. `search` depends only on `shared`. The proposal adds ArchUnit rules saying that "`shared` must not
   depend on any context" and "`search` must not depend on `catalog`".
3. **New dependencies, used only in `catalog.adapter.out`:**
   - JDK `java.net.http.HttpClient` for HTTP (no extra library);
   - **Jackson 3** (`tools.jackson.core:jackson-databind` 3.2.3, packages `tools.jackson.*`; its
     annotations remain in `com.fasterxml.jackson.annotation`) for JSON parsing of both platforms'
     responses. The owner chose Jackson 3 because the MCP Java SDK 2.0.1
     (`io.modelcontextprotocol.sdk:mcp`, slice 3) uses `mcp-json-jackson3` by default.
     The project's own code uses **only Jackson 3**, and only in adapters. Jackson 2
     (`com.fasterxml.jackson.core:jackson-databind`) may still appear on the classpath transitively,
     via `langchain4j-core` 1.20.2 in slice 2. The two versions use different packages, so they
     coexist, but our classes must never import `com.fasterxml.jackson.databind..`. The proposal adds an
     ArchUnit rule for this, next to the rules in item 2: "no class in `nz.sounie.blogmcp..` depends
     on `com.fasterxml.jackson.databind..` or `com.fasterxml.jackson.core..`".
   - jsoup (`org.jsoup:jsoup` 1.23.2) for HTML-to-text extraction and entity decoding, behind the `HtmlToText` port.
   (Versions as found on Maven Central on 2026-10-01.)
   None of them may appear in `domain` or `application`.
4. **File-based persistence**: posts and checkpoints are stored under `~/.local/share/blog-mcp/`
   (override `BLOG_MCP_DATA`). Sites are read from `~/.config/blog-mcp/sites.json` (override
   `BLOG_MCP_CONFIG`). Writes are atomic (write to a temporary file, then rename) so that a crash
   cannot leave a half-written post or checkpoint.
5. **Freshness strategy**: an incremental sync by a per-site checkpoint based on source timestamps,
   with an overlap margin, at startup and then every 6 hours. A full reconcile runs at most once a
   day and is the only mechanism that detects posts deleted upstream. (Posts are also withdrawn when
   they become password-protected, or when their site is removed from the configuration.) The overlap
   margin is 1 hour. The checkpoint advances per page only for
   sources that return changes oldest-first. Otherwise it advances only at the end of a complete run.
   Verified against the live sites: WordPress (`orderby=modified&order=asc`) is oldest-first, and
   Blogger (`orderby=updated`) is newest-first. Every "changed since" value sent to a platform carries
   an explicit UTC offset, because WordPress reads a zone-less `modified_after` as the site's local
   time (UTC+12 here).
6. **Body source is abstracted**: each `BlogSource` adapter produces plain-text `PostSnapshot`s with a
   `BodyCompleteness` (`FULL` or `SUMMARY`). For Blogger, the owner chose to switch the site feed
   to *Full* (catalog.md Q1, option (a)), so the adapter reads `content.$t` from
   `/feeds/posts/default`. It falls back to `summary` with `SUMMARY` only while an entry lacks content.
   No page scraping and no Blogger API v3 (so no API key to manage).
7. **WordPress taxonomy**: only tags are mapped, resolved through `/wp-json/wp/v2/tags`. Categories
   are ignored and never requested (catalog.md Q2).

## Consequences
- Search can be built and tested against `shared` events alone. Mapping between domain and
  integration events costs some boilerplate.
- Delivery is in-process and at most once. Search will need a rebuild path (slice 2).
- Posts deleted upstream may linger for up to about 24 hours, until the next reconcile.
- Blogger posts stay `SUMMARY` until the owner switches the feed to Full. After that, the next
  reconcile upgrades them through `PostRevised` (catalog.md AC-CAT-14).
- Adding a platform means adding a `BlogSource` adapter and a `Platform` value. The domain is unchanged.
- `tools.jackson.core:jackson-databind` 3.2.3 and `org.jsoup:jsoup` 1.23.2 must be added to `gradle/libs.versions.toml` as `implementation`
  dependencies. They are used only from `catalog.adapter.out`.
- When slice 2 adds LangChain4j, Jackson 2 will appear in the dependency tree. That is expected,
  and the ArchUnit rule keeps our own code on Jackson 3.
