# Catalog bounded context

Status: approved by the owner on 2026-10-01 (decisions recorded in section 8)
Package: `nz.sounie.blogmcp.catalog`
Feature tag for acceptance criteria: `CAT`

## 1. Purpose

The catalog knows **which posts exist** on the owner's configured blogs and keeps an up-to-date
**local copy** of each one (metadata plus plain-text body). It announces every change to a post's
public content so that other contexts can react to it.

The catalog is responsible for:
- reading the configured sites and validating that configuration;
- syncing each site incrementally from its blog platform, and doing a full reconcile once a day;
- turning platform HTML into plain text, and WordPress tag IDs into tag names (WordPress categories are ignored);
- answering "give me this post" by post ID or by URL.

It is **not** responsible for:
- chunking, embedding, vector indexing or ranking (the `search` context, slice 2);
- the MCP protocol or tool schemas (the `mcp` adapter, slice 3);
- writing to the blogs. The catalog is read-only towards the platforms.

## 2. Ubiquitous language

| Term | Meaning | Code name |
|---|---|---|
| Site | One blog the owner has configured. It has a site ID, a platform and a base URL. Sites come from configuration, and the catalog never creates them. | `Site` |
| Site ID | Owner-chosen, stable, lower-case slug that identifies a site (`[a-z0-9-]{1,40}`, e.g. `sounie-wp`). It is part of every post ID, so renaming it re-identifies every post of that site. | `SiteId` |
| Platform | The blogging software behind a site: `WORDPRESS` or `BLOGGER`. In the sites configuration, platform names match case-insensitively (`wordpress`, `WordPress` and `WORDPRESS` are all accepted). | `Platform` |
| Base URL | Absolute `https` URL of the site (scheme, host and optionally a path, but no query or fragment). Its host is the **site host**. | `Site.baseUrl()` |
| Sites configuration | The validated set of all sites, plus the sync interval (slice 3b, owner decision 2026-10-03). It is valid only as a whole (see invariants). It is read from `sites.json`; the file keeps that name. | `SitesConfiguration` |
| Sync interval | (slice 3b, proposed in `docs/domain/app.md` 3.8) How long the app waits between the end of one sync-and-reconcile run and the start of the next. It is set by `"syncEveryHours"` in `sites.json`: a whole number, at least 1, default **24** when omitted. It is consumed only by the `app` scheduler. The daily reconcile rule is unchanged, so with 24 hours each run is in practice a reconcile. | `SyncInterval`, `SyncIntervalSetting` (sealed: `Omitted`, `WholeHours`, `Unparseable`), both in `catalog.domain.site` |
| Post | One published blog post as the catalog knows it. It is the aggregate root. | `Post` |
| Source post ID | The platform's own identifier for a post, as a string. WordPress: the numeric `id`. Blogger: the digits after `.post-` in `id.$t`. Never derived from the slug or URL, because those can change. | `SourcePostId` |
| Post ID | The catalog-wide identity of a post: site ID plus source post ID. Its external form is `<siteId>:<sourcePostId>`, e.g. `sounie-wp:123`. | `PostId` |
| Title | Plain-text title. Markup is removed **only where the platform declares the title as HTML**. WordPress `title.rendered` is always HTML, so its tags are stripped and its entities decoded. A Blogger `title.$t` is stripped only when `title.type` is `html` or `xhtml`. When the type is `text` or missing, the title is kept verbatim. So a text title such as `Generics: List<String> & co` is preserved exactly. A title may be empty, but is never null. | `Title` |
| Canonical URL | The public address of the post (WordPress `link`, Blogger `link[rel=alternate]`). It must be absolute `https` on the site host. | `CanonicalUrl` |
| Published at / Updated at | Instants (UTC) at which the platform says the post was first published and last modified. | `publishedAt`, `updatedAt` |
| Tag | Plain-text label attached to a post. Tags are compared case-insensitively and the casing seen first is kept. WordPress **tags** (resolved from their IDs) and Blogger labels become tags. WordPress **categories are ignored entirely**. | `Tag` |
| Body | The post content as plain text, produced from the platform HTML by **text extraction**. | `Body` |
| Body completeness | Whether the body is the `FULL` post text or only a `SUMMARY` (a truncated excerpt from a short feed). | `BodyCompleteness` |
| Text extraction | Turning HTML into plain text: drop `script`/`style`, decode entities, put a paragraph break after block elements, keep whitespace inside `pre`, and collapse other whitespace. | `HtmlToText` port (adapter-implemented) |
| Post snapshot | What a blog source currently says about one post, already mapped to catalog language. It is the input to publish and revise. | `PostSnapshot` |
| Source entry | One item returned by a blog source. It is one of: **available** (carries a post snapshot), **not public** (for example a password-protected post) or **malformed** (could not be mapped, with a reason and the source post ID if one could be read). | `SourceEntry` (sealed: `Available`, `NotPublic`, `Malformed`) |
| Blog source | Port that pages through the source entries of a site, optionally only those changed since a given instant. | `BlogSource` (domain port) |
| Source page | One page of source entries, plus whether more pages follow. | `SourcePage` |
| Change order | The order in which a blog source returns changed entries: `OLDEST_FIRST`, `NEWEST_FIRST` or `UNORDERED`. It decides when the checkpoint may advance. WordPress is `OLDEST_FIRST` and Blogger is `NEWEST_FIRST` (both verified). | `ChangeOrder` |
| Publish (a post) | Add a post the catalog has not seen before. | `Post.publish(...)` |
| Revise (a post) | Apply a snapshot to a known post, checked against the post's site exactly like publish. A revision is **material** only if the title, body, body completeness, tags, canonical URL or published-at changed. | `Revision Post.revise(Site site, PostSnapshot snapshot)` |
| Withdraw (a post) | Remove a post from the catalog because it is no longer publicly listed. | `Post.withdraw(reason)` |
| Withdrawal reason | `NO_LONGER_LISTED` (missing from a complete reconcile), `NO_LONGER_PUBLIC` (seen as not public, e.g. password-protected) or `SITE_REMOVED` (its site is no longer in the sites configuration; AC-CAT-32). | `WithdrawalReason` |
| Sync | One run that brings a site's posts up to date with its blog source. A sync is either **incremental** (from the checkpoint) or a **reconcile**. | `SyncSite` use case |
| Checkpoint | Per-site high-water mark: the latest `updatedAt` up to which every changed entry is known to have been handled. It is absent before the first successful sync. | `SyncCheckpoint` aggregate |
| Overlap margin | How far before the checkpoint an incremental sync starts asking for changes, to tolerate equal timestamps and paging shifts. It is fixed at **1 hour** (owner decision, Q4). It does **not** need to cover site timezone offsets, because sources always send an explicit UTC offset (see 3.4). | `OverlapMargin` |
| Reconcile | A full pass over every entry of a site, ignoring the checkpoint. It applies all snapshots and then withdraws posts that were not listed. A reconcile is **due** only when the last one is *strictly* older than 24 hours, or there has never been one. `SyncAllSites.run(RECONCILE)` forces a reconcile of every site, whether due or not. | `SyncSite` in `RECONCILE` mode |
| Sync report | Outcome of one sync: mode, outcome, pages fetched, counts of published, revised, unchanged and withdrawn posts, skipped entries with their reasons, warnings, and the checkpoint before and after. `Touched` and `Stale` revisions count as **unchanged**. A stored post that is seen as not public counts as **withdrawn**. | `SyncReport` |
| Sync warning | Something the owner should know about that did not stop an entry being applied. Kinds: `SOURCE_NOTE` (a source-side oddity, e.g. an unresolvable WordPress tag ID); `EMPTY_LISTING_WITHDRAWALS_SUPPRESSED` (a complete reconcile listed zero entries, so nothing was withdrawn); `UNIDENTIFIED_MALFORMED_ENTRY_WITHDRAWALS_SUPPRESSED` (a reconcile met a malformed entry with no readable source post ID, so nothing was withdrawn). | `SyncWarning` (with `SyncWarning.Kind`) |
| Sync outcome | `COMPLETED` (every page was handled), `PARTIAL` (failed after at least one page was handled), `FAILED` (no page was handled) or `SKIPPED` (a sync for the same site was already running). | `SyncOutcome` |
| Skipped entry | A source entry that was not applied, with its source post ID (if readable) and reason: `MALFORMED`, `NOT_PUBLIC` or `DUPLICATE_CANONICAL_URL`. An unresolvable WordPress tag ID is *not* a skip; it is a `SyncWarning` of kind `SOURCE_NOTE` (AC-CAT-8). | `SkippedEntry` |
| Get post | Look up one post by post ID or by URL. | `GetPost` use case |
| Post reference | (slice 3, proposed in `docs/domain/app.md` 3.7, pending owner approval) The free text given to `get_post`. It is a **URL** if the trimmed text starts with a scheme and `://`, and a **post ID** otherwise. A reference of either form that does not parse is `InvalidPostReference`. | `PostReference` (sealed `ById`, `ByUrl`), `GetPost.byReference(String)` |
| Post file / checkpoint file | (slice 3, proposed) The persisted form of one post or one checkpoint: one JSON file each under the data directory, written atomically. An unreadable file is quarantined, and the startup sync becomes a reconcile (`docs/domain/app.md` 3.3). | `FilePostRepository`, `FileSyncCheckpointRepository` (`adapter.out`) |

## 3. Aggregates

### 3.1 `Post` (aggregate root)

State: `PostId id`, `CanonicalUrl url`, `Title title`, `Body body`, `BodyCompleteness completeness`,
`Set<Tag> tags`, `Instant publishedAt`, `Instant updatedAt`.

Behaviour:
- `static Published Post.publish(Site site, PostSnapshot s)` creates the post and returns it with a `PostPublished` event.
- `Revision revise(Site site, PostSnapshot snapshot)` first enforces invariants 1 and 2, exactly like `publish`: it throws `PostIdentityMismatch` if the snapshot is for another post, and `CanonicalUrlNotOnSite` if the URL is not https on `site`'s host. It then returns one of:
  - `Revision.Changed(PostRevised event)` when the snapshot is not older and something material changed;
  - `Revision.Touched` when only `updatedAt` moved forward (the new `updatedAt` is recorded, and no event is raised);
  - `Revision.Unchanged` when nothing changed;
  - `Revision.Stale` when the snapshot's `updatedAt` is older than the stored one (it is ignored).
- `PostWithdrawn withdraw(WithdrawalReason reason)`. The post is then deleted from the repository. No tombstone is kept, so a post that comes back is published again (AC-CAT-24).

Invariants:
1. **Identity**: `PostId` = (`SiteId`, `SourcePostId`). It is assigned once and never changes. A snapshot whose ID belongs to a different post is rejected by `revise` (`PostIdentityMismatch`).
2. **Canonical URL**: absolute, scheme `https`, and host equal (ignoring case) to the site host. This is checked by both `publish` and `revise`. Otherwise the snapshot is invalid (`CanonicalUrlNotOnSite`) and the entry is skipped as malformed. An `http` link is **never** upgraded to `https`; it makes the entry malformed (owner decision, Q7; AC-CAT-16).
3. **Timestamps**: `updatedAt` is never earlier than `publishedAt`. If a source reports `updated < published`, the snapshot normalises `updatedAt := publishedAt`. A post's `updatedAt` **never moves backwards**: a stale snapshot is ignored (`Revision.Stale`).
4. **Material change**: `PostRevised` is raised only when at least one of title, body, completeness, tag set (compared without regard to order or case), canonical URL or published-at differs. A snapshot with the *same* `updatedAt` but different content (for example after a short feed was switched to full) is still applied.
5. **Tags**: trimmed, not blank, de-duplicated without regard to case. An empty set is allowed.
6. **Body**: never null, and may be empty.

Cross-aggregate rule, checked by the `SyncSite` use case against the repository rather than by `Post`:
- **A canonical URL belongs to at most one post.** If an available entry has a canonical URL that a *different* post already holds, the entry is skipped with reason `DUPLICATE_CANONICAL_URL` and reported.

### 3.2 `SyncCheckpoint` (aggregate root, one per site)

State: `SiteId siteId`, `Optional<Instant> changesSeenUpTo`, `Optional<Instant> lastReconciledAt`.

Behaviour and invariants:
1. `advanceTo(Instant t)` moves `changesSeenUpTo` forward only. A value earlier than or equal to the current one is a no-op, so the checkpoint **never moves backwards**.
2. The checkpoint uses **source timestamps** (the platform's `updatedAt` values), never the local clock. This makes it immune to skew between the local clock and the server clock.
3. `changesSinceForNextSync(OverlapMargin m)` returns `changesSeenUpTo - m`, or empty (meaning a full fetch) when there is no checkpoint yet. The margin is 1 hour (owner decision, Q4).
4. **When it advances** (enforced by `SyncSite`, using the source's `ChangeOrder`):
   - `OLDEST_FIRST`: after a page has been fully handled (every entry applied, skipped or reported, and every changed post saved), the checkpoint advances to the greatest `updatedAt` on that page and is saved. A failure on a later page leaves it at the last fully handled page.
   - `NEWEST_FIRST` or `UNORDERED`: the checkpoint advances only once, after the **last** page, to the greatest `updatedAt` seen in the run. A failure leaves it unchanged. Posts already saved stay saved, and seeing them again later causes no event.
   - Malformed and not-public entries with a readable `updatedAt` count towards the high-water mark, so one permanently bad entry cannot pin the checkpoint. A daily reconcile retries them.
5. `markReconciled(Instant now)` sets `lastReconciledAt`. It is only called after a reconcile with outcome `COMPLETED`. A reconcile is due when `lastReconciledAt` is empty or *strictly* older than 24 hours (exactly 24 hours is not yet due). This uses the local clock, which is acceptable for scheduling. A reconcile that withdrew nothing because of a suppression warning does not call `markReconciled`, so it is retried on the next run.

### 3.3 `Site` and `SitesConfiguration` (reference data, not aggregates)

`Site(SiteId id, Platform platform, URI baseUrl)` is an immutable value that the catalog reads but never
changes. `SitesConfiguration` validates the whole list and reports **all** violations together in
one `InvalidSitesConfiguration` exception:
- (slice 3b, proposed; `docs/domain/app.md` AC-APP-36 to 39) an optional top-level `syncEveryHours`, which must be a whole JSON
  number of at least 1 if present (default 24 hours). A value below 1, or one that is not a whole number (`"24"`, `24.5`, `true`,
  `null`), is a violation reported with the others;
- at least one site. An empty list is invalid, and a **missing** configuration file is a startup error (`SitesConfigurationMissing`, raised by the `SiteDirectory` adapter) naming the path it looked at (owner decision, Q6; AC-CAT-25);
- site IDs unique and matching `[a-z0-9-]{1,40}`;
- platform is one of the supported values, matched case-insensitively (an unknown string such as `"ghost"` is a violation, not a crash);
- base URL absolute, `https`, with a host, and with no query or fragment;
- no two sites with the same base URL.

Loading the file from `~/.config/blog-mcp/sites.json` (or `BLOG_MCP_CONFIG`) is an adapter concern
behind the domain port `SiteDirectory`.

### 3.4 Ports (in `catalog.domain`)

- `BlogSource`: `ChangeOrder changeOrder()`, `SourcePage fetch(Site site, Optional<Instant> changedSince, PageCursor cursor)`. It throws `SourceUnavailable` on transport or HTTP failure. Adapters choose by `Platform`.
  Contract for every implementation: `changedSince` is an `Instant` and must be sent to the platform **with an explicit UTC offset** (`Z` or `+00:00`), never as a zone-less local date-time. Zone-less values are interpreted in the site's local time by WordPress (AC-CAT-5).
  **No false completeness**: a source must never report a page as the last one when the listing may be truncated.
  - A response whose body has an unexpected shape (not the expected JSON object or array, or a missing entries container where one is required) is `SourceUnavailable`, never an empty page.
  - A missing or unparseable total count (`X-WP-TotalPages`, `openSearch$totalResults`) means "keep paging until a short or empty page", never "no more pages".
  - Why: a reconcile that wrongly believes it saw the complete listing would withdraw every post it did not see.
- `PostRepository`: `findById(PostId)`, `findByCanonicalUrl(CanonicalUrl)`, `findIdsBySite(SiteId)`, `findSiteIds()`, `save(Post)`, `delete(PostId)`.
- `SyncCheckpointRepository`: `find(SiteId)`, `save(SyncCheckpoint)`, `delete(SiteId)`.
- `SiteDirectory`: `SitesConfiguration load()`.
- `HtmlToText`: `String extract(String html)` and `String decodeEntities(String text)`. jsoup implements it in `adapter.out`. Each `BlogSource` adapter uses it while building `PostSnapshot`s, so the domain only ever sees plain text.

### 3.5 Application use cases (`catalog.application`)

- `SyncSite(siteId, mode = INCREMENTAL | RECONCILE)` returns a `SyncReport`. For each entry it loads, publishes or revises, and saves **one post at a time** (one aggregate per transaction). It then advances and saves the checkpoint according to 3.2. A reconcile withdraws only if the run `COMPLETED` (see AC-CAT-21 to AC-CAT-23). At most one sync runs per site at any moment, and a second request returns `SKIPPED`.
- `SyncAllSites(mode)` runs `SyncSite` for each configured site independently. A failure in one site does not stop the others. `run(INCREMENTAL)` uses `RECONCILE` only for a site whose reconcile is due. `run(RECONCILE)` forces a reconcile of every site. Before syncing, it withdraws every stored post whose site ID is not in the (valid) sites configuration, with reason `SITE_REMOVED`, and deletes that site's checkpoint (owner decision, Q5; AC-CAT-32).
- `GetPost(PostId | url)` returns `Optional<PostView>`. URL lookup **normalises**: lower-case scheme and host, `http` is treated as `https`, the fragment is dropped, the default port is dropped, and a trailing slash on the path is ignored. The query string is kept.
- Scheduling (run at startup in the background, then every 6 hours) lives in an inbound adapter.

## 4. Domain events

Domain events live in `catalog.domain`. They are raised by `Post` and **published only after the
post has been saved**, in the order the entries were handled. The catalog application maps them
to integration events in `nz.sounie.blogmcp.shared` (published language, JDK types only) for the
in-process event bus. `search` subscribes to those and never imports `catalog.domain` (ADR 0003).

| Event | Trigger | Payload |
|---|---|---|
| `PostPublished` | `Post.publish` (first time this post ID is seen, including a post coming back after withdrawal) | post ID, site ID, canonical URL, title, body, completeness, tags, publishedAt, updatedAt |
| `PostRevised` | `Post.revise` with a material change | same full state as `PostPublished`, plus `Set<RevisedAspect> changed` (`TITLE`, `BODY`, `COMPLETENESS`, `TAGS`, `URL`, `PUBLISHED_AT`) so that search can skip re-embedding when only tags or the URL changed |
| `PostWithdrawn` | `Post.withdraw` (reconcile found the post missing; the post was seen as not public; or its site was removed from the configuration) | post ID, site ID, canonical URL, reason |

The events carry their full state (event-carried state transfer), so `search` never needs to query
the catalog. Delivery is in-process and at most once, so `search` will need a rebuild path in slice 2 (Q8).

## 5. Context map

- **WordPress REST API and Blogger feed (upstream, external)**: the catalog is a *conformist on transport and an anti-corruption layer on meaning*. Each `BlogSource` adapter translates platform JSON into `SourceEntry`/`PostSnapshot`, so no platform terms (`rendered`, `$t`, `thr$total`, term IDs) leak into the domain.
- **catalog to search (customer/supplier via published language)**: catalog is the supplier. The contract is the integration events in `shared`. `search` depends on `shared` only, and `shared` depends on no context.
- **catalog to mcp adapter (slice 3)**: `get_post` calls the `GetPost` inbound port.

## 6. Platform mapping notes (for the adapter implementers; facts supplied by the lead)

- **WordPress** (`blog2.sounie.nz`): `GET /wp-json/wp/v2/posts?per_page=100&page=N&orderby=modified&order=asc[&modified_after=…]`. Pages come from `X-WP-TotalPages`. `date_gmt`/`modified_gmt` are zone-less and mean UTC. `content.protected=true` maps to `NotPublic`. Tag names come only from `/wp-json/wp/v2/tags`, resolving the post's `tags` IDs. The `categories` field is ignored, and `/wp/v2/categories` is never requested (owner decision, Q2). Completeness is always `FULL`.
  - `ChangeOrder` is `OLDEST_FIRST`. Verified: `orderby=modified&order=asc` returned all 20 posts in ascending `modified_gmt` order.
  - `modified_after` must carry an explicit UTC offset, e.g. `2026-09-20T05:00:00Z`. Verified: without a zone suffix, WordPress compares against the site's **local** time (the site is UTC+12). With `Z` or `+00:00`, it compares correctly as UTC.
  - *Optional adapter guidance, not a domain rule:* add `_fields=id,date_gmt,modified_gmt,link,status,type,title,content,tags` to shrink responses. Do the same with `_fields=id,name` on the tag lookup. If used, an adapter test must show that every field the mapping needs is still requested.
- **Blogger** (`blog.elegant-solutions.london`): `GET /feeds/posts/default?alt=json&max-results=150&start-index=N[&updated-min=…&orderby=updated]`. `start-index` is 1-based and advances by the number of entries received. Paging stops when a page has fewer than 150 entries, the `entry` key is absent, or `start-index > openSearch$totalResults`. The source post ID is the digits after `.post-`. Tags come from `category[].term`. `updated-min` is sent as RFC 3339 with an explicit offset.
  - Title: honour `title.type`. If it is `html` or `xhtml`, strip markup and decode entities. If it is `text` or missing, keep `title.$t` verbatim. All 209 real entries are `type: text` (AC-CAT-6).
  - `ChangeOrder` is `NEWEST_FIRST`. Verified: with `orderby=updated` the first of 150 entries was from 2026-04-13 and the last from 2010-01-19. So the Blogger checkpoint advances only after the final page of a complete sync (AC-CAT-4, AC-CAT-19).
  - Body source (owner decision, Q1, option (a)): the owner switches the site feed to *Full*, so `/feeds/posts/default` carries `content.$t`, which goes through text extraction with completeness `FULL`. Until then, or whenever an entry has no `content`, the snapshot carries the `summary` with completeness `SUMMARY`. Such posts upgrade through AC-CAT-14. No page scraping and no Blogger API v3.

## 7. Acceptance criteria

### Paging and incremental sync

**AC-CAT-1: First sync of a site with no checkpoint fetches everything.**
Given site `sounie-wp` (WordPress) with no checkpoint, and a source holding 3 published posts updated at T1 < T2 < T3,
When `SyncSite(sounie-wp, INCREMENTAL)` runs,
Then the source is asked for all entries (no `modified_after`), 3 posts are stored, 3 `PostPublished` events are published, the checkpoint is T3, and the report outcome is `COMPLETED` with `published=3`.

**AC-CAT-2: WordPress paging follows `X-WP-TotalPages`.**
Given a WordPress site whose API reports `X-WP-Total: 230` and `X-WP-TotalPages: 3`,
When the site is synced,
Then pages 1, 2 and 3 are requested with `per_page=100`, no page 4 is requested, and 230 posts are stored.

**AC-CAT-3: Empty site.**
Given a WordPress site reporting `X-WP-Total: 0` (or a Blogger feed with `openSearch$totalResults: 0` and no `entry` key),
When the site is synced,
Then exactly one request is made, no posts or events are produced, the checkpoint stays absent, and the outcome is `COMPLETED`.

**AC-CAT-4: Blogger paging by start-index.**
Given a Blogger site whose feed reports `openSearch$totalResults: 209`,
When the site is synced,
Then requests are made with `max-results=150` and `start-index=1`, then `start-index=151`, no third request is made, and 209 posts are stored with source post IDs taken from the digits after `.post-`.
And because the Blogger source is `NEWEST_FIRST`, the checkpoint is **not** saved after the first page, and is set once after the second page to the greatest `updatedAt` seen in the run.

**AC-CAT-5: Incremental sync starts from checkpoint minus overlap margin.**
Given site `sounie-wp` with checkpoint `2026-09-20T01:20:47Z` and the overlap margin of 1 hour,
When `SyncSite(sounie-wp, INCREMENTAL)` runs,
Then the source is asked for entries changed since `2026-09-20T00:20:47Z`, and only the returned entries are applied.
And the WordPress request carries `modified_after=2026-09-20T00:20:47Z` (Blogger: `updated-min=2026-09-20T00:20:47Z` or the `+00:00` form). That is, an explicit UTC offset, never a zone-less value such as `2026-09-20T00:20:47`.

### Mapping and text

**AC-CAT-6: HTML becomes plain text, and entities in titles are decoded.**
Given a WordPress entry with `title.rendered` = `Don&#8217;t &amp; &lt;panic&gt;` and `content.rendered` = `<p>One&nbsp;line</p><script>x()</script><pre>  a\n  b</pre><p>Two</p>`,
When it is applied,
Then the post title is `Don’t & <panic>`, and the body contains `One line`, a paragraph break, the `pre` text with its leading spaces and newline kept, and `Two`, with no `x()` and no tags.
And given an **HTML** title `Hello <em>World</em> &amp; more` (a WordPress `title.rendered`, or a Blogger `title.$t` with `title.type` `html` or `xhtml`), the post title is `Hello World & more` (markup removed, entities decoded).
And given a Blogger **text** title `Generics: List<String> & co` (`title.type` `text` or missing), the post title is exactly `Generics: List<String> & co` (kept verbatim, nothing stripped).

**AC-CAT-7: Timestamps are interpreted as UTC.**
Given a WordPress entry with `date_gmt` `2026-09-20T01:20:47` (no zone), and a Blogger entry with `updated.$t` `2026-03-23T20:53:00.001+00:00`,
When they are applied,
Then the stored instants are `2026-09-20T01:20:47Z` and `2026-03-23T20:53:00.001Z`.

**AC-CAT-8: WordPress tag IDs resolve to tag names, and categories are ignored.**
Given an entry with `tags: [5, 7, 99]` and `categories: [1, 3]`, where `/wp-json/wp/v2/tags` defines tag 5 `java` and tag 7 `Java`, and tag 99 does not exist,
When it is applied,
Then the post's tags are `{java}` (`Java` collapsed as a case-insensitive duplicate), the unknown tag ID 99 is dropped and reported as a `SyncWarning` of kind `SOURCE_NOTE`, the post is **not** skipped, the categories contribute nothing, and no request is made to `/wp-json/wp/v2/categories`.

**AC-CAT-9: Blogger labels become tags.**
Given a Blogger entry with `category: [{term: "DDD"}, {term: " ddd "}, {term: "Java"}]`,
When it is applied,
Then the post's tags are `{DDD, Java}`.

### Change detection

**AC-CAT-10: An unchanged post seen again raises no event.**
Given stored post `sounie-wp:123` and a snapshot with identical `updatedAt` and content,
When it is applied,
Then `revise` returns `Unchanged`, no event is published, and the report counts it as `unchanged`.

**AC-CAT-11: A revised post raises exactly one `PostRevised`.**
Given stored post `sounie-wp:123` with title `Old`, and a snapshot with a later `updatedAt`, title `New` and otherwise identical content,
When it is applied,
Then exactly one `PostRevised` is published with `changed = {TITLE}`, and the stored post has title `New` and the new `updatedAt`.

**AC-CAT-12: A newer timestamp without a material change records the time but raises no event.**
Given stored post `sounie-wp:123` updated at T1, and a snapshot updated at T2 > T1 with identical content,
When it is applied,
Then `revise` returns `Touched`, the stored `updatedAt` is T2, and no event is published.

**AC-CAT-13: A stale snapshot is ignored.**
Given stored post `sounie-wp:123` updated at T2, and a snapshot updated at T1 < T2 with a different title,
When it is applied,
Then `revise` returns `Stale`, the stored post is unchanged, and no event is published.

**AC-CAT-14: Upgrading a summary to the full body is a revision.**
Given stored Blogger post `elegant:371460637286630063` with completeness `SUMMARY`, and a snapshot with the **same** `updatedAt` but completeness `FULL` and a longer body,
When it is applied,
Then one `PostRevised` is published with `changed = {BODY, COMPLETENESS}`.

### Exclusions, invalid data and failure

**AC-CAT-15: A password-protected WordPress post is excluded.**
Given a WordPress entry with `content.protected: true`,
When it is applied and the post is unknown, then it is not stored, no event is published, and it is reported as skipped with reason `NOT_PUBLIC`;
and when the post was already stored, then it is withdrawn with reason `NO_LONGER_PUBLIC` and one `PostWithdrawn` is published.

**AC-CAT-16: A malformed entry is skipped and reported without aborting the site.**
Given a page of 3 entries where the middle one is malformed, for each of: missing `id`; unparseable `modified_gmt`; canonical URL `http://blog2.sounie.nz/x` (never upgraded to https); canonical URL on another host `https://evil.example/x`,
When the page is applied,
Then the other 2 entries are stored with their events, the malformed entry appears in the report with its source post ID (if readable) and reason, the outcome is `COMPLETED`, and the checkpoint advances as for a normal page.
And this applies to revisions too: if the malformed entry is for an already stored post (for example its canonical URL is now `http://…` or on another host), `revise` rejects it, the stored post is left unchanged, no event is published, and the entry is reported as skipped `MALFORMED`.

**AC-CAT-17: Duplicate canonical URL.**
Given stored post `sounie-wp:123` with URL `https://blog2.sounie.nz/a/`, and an entry for source post ID `456` with the same canonical URL,
When it is applied,
Then `sounie-wp:456` is not stored and is reported with reason `DUPLICATE_CANONICAL_URL`, and `sounie-wp:123` is untouched.

**AC-CAT-18: An HTTP error mid-sync keeps the checkpoint at the last fully handled page (oldest-first source, e.g. WordPress).**
Given an `OLDEST_FIRST` source with 3 pages whose greatest `updatedAt` values are P1 < P2 < P3, and the request for page 3 fails with HTTP 503,
When the site is synced,
Then the posts from pages 1 and 2 are stored with their events, the checkpoint is P2, the outcome is `PARTIAL` with the error in the report, and the next incremental sync asks for changes since P2 minus the margin.

**AC-CAT-19: An HTTP error with a newest-first source leaves the checkpoint unchanged (e.g. Blogger).**
Given a `NEWEST_FIRST` source (the same applies to `UNORDERED`) with checkpoint C and 2 pages of changes, and page 2 fails,
When the site is synced,
Then the posts from page 1 are stored with their events, the checkpoint stays C, and the outcome is `PARTIAL`. When the next sync sees those posts again, it publishes no events for them (AC-CAT-10).

**AC-CAT-20: A failure on the first page fails the sync and changes nothing.**
Given the source fails on page 1,
When the site is synced,
Then no posts change, the checkpoint is unchanged, and the outcome is `FAILED`.

### Reconcile and withdrawal

**AC-CAT-21: A complete reconcile withdraws posts that are no longer listed.**
Given stored posts `sounie-wp:1`, `sounie-wp:2` and `sounie-wp:3`, and a source that now lists only 1 and 3,
When `SyncSite(sounie-wp, RECONCILE)` completes,
Then `sounie-wp:2` is deleted, one `PostWithdrawn(reason = NO_LONGER_LISTED)` is published, and `lastReconciledAt` is set.

**AC-CAT-22: A reconcile that is incomplete or empty withdraws nothing.**
Given stored posts for a site, and a reconcile that either (a) ends `PARTIAL` or `FAILED`, or (b) completes but lists zero entries,
When it finishes,
Then no post is withdrawn, `lastReconciledAt` is not updated, and in case (b) the report carries the warning `EMPTY_LISTING_WITHDRAWALS_SUPPRESSED`.

**AC-CAT-23: Malformed entries protect against withdrawal.**
Given stored post `sounie-wp:2` and a complete reconcile in which the entry for source post ID `2` is malformed,
When the reconcile finishes,
Then `sounie-wp:2` is **not** withdrawn.
And given a complete reconcile containing a malformed entry whose source post ID cannot be read,
Then no posts of that site are withdrawn in that run, the report carries the warning `UNIDENTIFIED_MALFORMED_ENTRY_WITHDRAWALS_SUPPRESSED`, and `lastReconciledAt` is unchanged, so the reconcile is retried on the next run.

**AC-CAT-24: A withdrawn post that comes back is published again.**
Given `sounie-wp:2` was withdrawn earlier,
When a later sync sees it as available,
Then it is stored and one `PostPublished` is published.

### Configuration and orchestration

**AC-CAT-25: Sites configuration is validated as a whole.**
Given a configuration with two sites both using ID `blog`, one with platform `ghost`, and one with base URL `http://blog2.sounie.nz`,
When it is loaded,
Then `InvalidSitesConfiguration` is raised listing all three violations (duplicate ID, unsupported platform, non-https base URL), and no sync starts. The same exception is raised for an empty site list, an ID that does not match `[a-z0-9-]{1,40}`, a base URL with a query or fragment, and two sites with the same base URL.
And a platform written as `WordPress` or `blogger` is accepted (case-insensitive match).
And given no configuration file at the resolved path (`BLOG_MCP_CONFIG` or `~/.config/blog-mcp/sites.json`), startup fails with `SitesConfigurationMissing` naming that path, and no sync starts.

**AC-CAT-26: Sites sync independently.**
Given two configured sites where the first site's source fails on page 1,
When `SyncAllSites` runs,
Then the second site is still synced, and the result holds one `FAILED` report and one `COMPLETED` report.

**AC-CAT-27: Only one sync per site at a time.**
Given a sync of `sounie-wp` is in progress,
When another `SyncSite(sounie-wp, …)` is requested,
Then it returns immediately with outcome `SKIPPED` and changes nothing.

**AC-CAT-28: A reconcile runs when it is due.**
Given site `sounie-wp` with `lastReconciledAt` 25 hours ago (or never), and site `elegant` reconciled 2 hours ago,
When `SyncAllSites` runs,
Then `sounie-wp` is synced in `RECONCILE` mode and `elegant` in `INCREMENTAL` mode.
And a site reconciled exactly 24 hours ago is not yet due (it must be strictly older). And `SyncAllSites.run(RECONCILE)` reconciles both sites regardless.

**AC-CAT-32: Posts of a site removed from the configuration are withdrawn.**
Given stored posts `old-blog:1` and `old-blog:2` and a checkpoint for `old-blog`, and a valid sites configuration that no longer contains `old-blog`,
When `SyncAllSites` runs,
Then both posts are deleted, two `PostWithdrawn(reason = SITE_REMOVED)` events are published, the `old-blog` checkpoint is deleted, and the configured sites are synced as usual.

### Get post

**AC-CAT-29: Get a post by ID.**
Given stored post `sounie-wp:123`,
When `GetPost` is called with `sounie-wp:123`,
Then the post's ID, site, title, canonical URL, tags, timestamps, body and completeness are returned.

**AC-CAT-30: Get a post by URL, with normalisation.**
Given stored post with canonical URL `https://blog2.sounie.nz/2026/09/20/hello/`,
When `GetPost` is called with `http://BLOG2.sounie.nz/2026/09/20/hello#comments` or with `https://blog2.sounie.nz/2026/09/20/hello/`,
Then that post is returned.

**AC-CAT-31: Unknown or invalid post reference.**
Given no post `sounie-wp:999` and no post at `https://blog2.sounie.nz/nope/`,
When `GetPost` is called with either one,
Then an empty result is returned (not an exception).
And when it is called with `not-an-id` or a non-URL string, then `InvalidPostReference` is raised.

## 8. Decisions and open questions

- **Q1 (resolved 2026-10-01, owner): full Blogger text, option (a).** The owner will switch Blogger Settings, Site feed, "Allow blog feed" to *Full*. There is no scraping and no API v3. `BodyCompleteness` is kept: posts stored as `SUMMARY` before the switch upgrade to `FULL` through AC-CAT-14.
- **Q2 (resolved 2026-10-01, owner): tags only.** WordPress categories are ignored entirely. Only `tags` IDs are resolved, via `/wp-json/wp/v2/tags` (AC-CAT-8).
- **Q3 (resolved 2026-10-01 by the lead against the live sites):**
  - WordPress honours `orderby=modified&order=asc`, so it is `OLDEST_FIRST`.
  - `modified_after` is compared as UTC only when it has an explicit offset, so an explicit offset is now a source contract (3.4, AC-CAT-5).
  - Blogger `orderby=updated` sorts newest-first, so it is `NEWEST_FIRST` (AC-CAT-4, AC-CAT-19).
- **Q4 (resolved 2026-10-01, owner): the overlap margin is 1 hour** (3.2, AC-CAT-5). It does not need to cover timezone offsets (see Q3).
- **Q5 (resolved 2026-10-01, owner): when a site is removed from the configuration, its posts are withdrawn** with `SITE_REMOVED` and its checkpoint is deleted (3.5, AC-CAT-32).
- **Q6 (resolved 2026-10-01, owner): a missing configuration file or an empty site list is a startup error**, not a silent no-op (3.3, AC-CAT-25).
- **Q7 (resolved 2026-10-01, owner): an `http` canonical URL makes the entry malformed.** It is never upgraded to https (3.1 invariant 2, AC-CAT-16).
- **Lead decisions (2026-10-01, recorded at review and encoded in the tests):**
  - An unresolvable WordPress tag ID is a `SyncWarning` of kind `SOURCE_NOTE`, not a skipped entry (AC-CAT-8).
  - An unidentified malformed entry in a reconcile suppresses all withdrawals, raises `UNIDENTIFIED_MALFORMED_ENTRY_WITHDRAWALS_SUPPRESSED` and leaves `lastReconciledAt` unchanged, so the reconcile is retried on the next run (AC-CAT-23).
  - A reconcile is due only when the last one is strictly older than 24 hours (AC-CAT-28).
  - `SyncAllSites.run(RECONCILE)` forces a reconcile of every site (AC-CAT-28).
  - Platform names in the configuration match case-insensitively (AC-CAT-25).
  - `Post.revise(Site, PostSnapshot)` enforces identity and the canonical-URL invariant just as `publish` does (AC-CAT-16).
  - Sources must never report false completeness for a truncated listing (3.4).
  - Titles are plain text. Markup is removed only where the platform declares the title as HTML: always for WordPress `title.rendered`, and for Blogger only when `title.type` is `html` or `xhtml`. Blogger `text` titles are kept verbatim (AC-CAT-6). This was corrected in review loop 2.
- **Q8 (open, lead, for slice 2): event delivery.** Delivery is in-process and at most once, with no outbox. `search` will need a way to rebuild its index from the catalog (for example a `ListPosts` query or a replay), which is out of scope for this slice. *Resolved (slice 2, approved by the owner on 2026-10-02):* a pull contract `shared.query.CatalogPosts`, implemented by the catalog as `ListCatalogPosts`, and a fingerprint-based reconcile in search. See `docs/domain/search.md` section 5, ADR 0005 and AC-SRCH-31.
