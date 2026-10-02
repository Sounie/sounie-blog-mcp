# 6. Package structure and composition root

Date: 2026-10-03

## Status
Accepted (2026-10-03, by the owner)

## Context
After slices 1 (catalog) and 2 (search), a review of the package hierarchy found these problems:
- Each `domain` package was flat and wide (about 41 types each), and almost every type was public, so a context's real API couldn't be told apart from its internals.
- Ports were placed by two different conventions. `HtmlToText`, used only by adapters, sat in the domain. `BlogSource` and `SiteDirectory`, used only by use cases, sat in the domain, while search's `PostCatalog` sat in application.
- `WebAddress`, a value object, sat in `catalog.application`.
- Slice 3 (the MCP server, wiring and scheduler) spans both contexts and had no designated package.

## Decision
1. **Domain sub-packages by concept, with acyclic dependencies.** ArchUnit's `domain_sub_packages_are_free_of_cycles` enforces this.
   - **`catalog.domain`:** `site` ← `post` ← `sync`.
   - **`search.domain`:** `post` and `text` at the base; `embedding` → `text`; `index` → `embedding`, `post`, `text`; `query` → `index`, `embedding`, `post`, `text`; `reconcile` → `index`, `post`. Nothing in the domain depends on `query`.
   - **Placements chosen to keep the graph acyclic:**
     - `PostToIndex`, `Completeness` and `PostMatch` live in `index`.
     - `InvalidPostReference` moved to the catalog domain with `WebAddress`, which throws it.
   - **Visibility:** a type is public if it's used outside its sub-package, or if it appears in the public API of a public type: a parameter, return, field or thrown type. Otherwise it's package-private. ArchUnit's `public_api_exposes_only_public_types` enforces the second part. It was added after review found the first pass had hidden types such as `SiteFilter` that public signatures still exposed.
2. **Ports are split by consumer when that breaks a cycle.** `search.domain`'s `Embedder` became two ports:
   - `search.domain.embedding.PassageEmbedder` (`modelId`, `embedPassages`), used by indexing in the domain;
   - `search.application.QueryEmbedder` (`embedQuery`), used only by the `SearchPosts` use case, so it lives in application under rule 3.

   `OnnxEmbedder` implements both, with behaviour unchanged.
3. **Port placement: whoever uses it owns it.**
   - **Repositories** always live in the domain (DDD convention).
   - **Other ports** live in the innermost layer that calls them. `BlogSource` and `SiteDirectory` moved to `catalog.application`; `PostCatalog` stays in `search.application`, joined by `QueryEmbedder`; `PassageEmbedder`, `TokenCounter` and `VectorIndex` stay in the domain.
   - **Adapter-only abstractions** live in the adapter package. `HtmlToText` moved to `catalog.adapter.out`. It's public there, because the sources' public constructors take it and the composition root supplies `JsoupHtmlToText`.
4. **`WebAddress` moved to `catalog.domain.post`,** beside `CanonicalUrl`.
5. **Composition root `nz.sounie.blogmcp.app`.** It holds `Main`, the wiring, the scheduler and the MCP server (`app.mcp`), all added in slice 3. ArchUnit's `nothing_depends_on_the_composition_root` enforces that nothing depends on it. The existing context-cycle rule is unchanged: nothing points into `app`, so `app` can never be part of a cycle.
6. **`InProcessEventBus` stays in `shared.event` (owner decision).** Moving it to `app` would have required changing how `CatalogEventListener` subscribes. The bus is treated as part of the published language's in-process transport. It uses JDK types only.

## Consequences
- **Visibility:** each sub-package exposes a smaller, deliberate API. Some types had to become public because they're used across sub-packages (e.g. `WebAddress`, `WordSequence.strip`, the `recipePart` methods). That's the accepted cost of sub-packages, given Java has no hierarchical visibility.
- **New types:** these rules decide where a new type or port goes. A cycle is resolved by splitting a port by consumer or moving a boundary type, never by merging sub-packages.
- **PIT:** `targetClasses = nz.sounie.blogmcp.*.domain.*` already matches the sub-packages, since PIT's `*` also matches dots. The mutant count rose from 360 to 374 because `WebAddress` and `InvalidPostReference` are now domain code.
- **Behaviour:** this refactor is behaviour-free. Tests changed only `package`/`import` lines, plus `FakeEmbedder` implementing both new ports.
