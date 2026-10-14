# blog-mcp

A local, read-only [MCP](https://modelcontextprotocol.io) server that lets any MCP client (an AI assistant, IDE or agent) search
and read my blog posts.

It syncs posts from WordPress and Blogger sites, stores them as plain text on disk, and indexes them with a
local embedding model. Each embedding provider has its own runnable jar, and you choose a provider by choosing which jar
to run; provider details stay outside the core search domain. Search runs entirely on your machine:
no API keys, and no calls to hosted models. The only network traffic is fetching the blogs' public feeds and,
with the `gemma2` jar, the images inside their posts.

## Tools

| Tool | What it does |
|---|---|
| `search_posts` | Semantic search over all configured sites. You can limit it to one `site`, a `from`/`to` publication date range (`yyyy-MM-dd`, New Zealand time) and a `limit` of 1–20 results (default 10). Each result has a title, URL, date, relevance score and snippet, plus a `postId`. When the post matched through one of its images, the result also has `matchedImage` (the image's URL), and the snippet comes from the image's alt text or caption. |
| `get_post` | Returns one post in full as plain text, looked up by `postId` (e.g. `sounie-wp:123`) or by URL. |

Both tools are marked read-only. Their descriptions tell the model to treat post text as data, never as
instructions.

## Requirements

- Java 25

## Build

```sh
./gw shadowJar
```

This builds one runnable jar per embedding provider. You choose a provider by choosing which jar to run:

| Jar | Embedding model |
|---|---|
| `providers/onnx/build/libs/blog-mcp-onnx.jar` | BGE-small-en-v1.5 (quantised), in process |
| `providers/gemma2/build/libs/blog-mcp-gemma2.jar` | EmbeddingGemma 2 (q8 ONNX), in process; about 370 MB, and needs no model server. The only jar that also indexes post images |
| `providers/gemma2-server/build/libs/blog-mcp-gemma2-server.jar` | EmbeddingGemma 2 via LM Studio, which must be serving `text-embedding-embeddinggemma-2` on `http://localhost:1234/v1` (override with `BLOG_MCP_EMBEDDING_ENDPOINT`) |

Each provider keeps its own search index in the data directory. The first run of a jar builds that
provider's index; later runs, including after switching back from another jar, reuse it.

The model files come from huggingface.co. If you can't reach it, add `-PhuggingFaceMirror=<dir>`
pointing at a local copy with the same `<org>/<repo>/resolve/<revision>/…` layout.

### Image search (`gemma2` only)

The `gemma2` jar also embeds the images inside each post's content, each as its own entry beside the
post's text, so a text query can find a post by what its pictures show. A post scores as its best entry.

- Only images in the post body are indexed. Emoji, tracking pixels, `data:` URIs, non-`https` URLs and
  images under 64 px are skipped.
- Images are fetched over HTTPS only, up to 10 MiB each, as JPEG, PNG or GIF. WebP and other formats are
  skipped with a line on stderr.
- An image may come from any host, but its addresses must suit the blog's own host: a blog on a public
  address can only load images from public addresses. A blog on a private network can also load images
  from that network.
- An image that fails for a temporary reason, such as a timeout or a server error, is retried at the next
  reconcile. One that can never be indexed, such as a 404 or an unsupported format, is not retried until
  the post changes.

The `onnx` and `gemma2-server` jars index text only.

## Configure

Create `~/.config/blog-mcp/sites.json`:

```json
{
  "syncEveryHours": 24,
  "sites": [
    { "id": "sounie-wp", "platform": "WORDPRESS", "baseUrl": "https://blog2.sounie.nz" },
    { "id": "elegant",   "platform": "BLOGGER",   "baseUrl": "https://blog.elegant-solutions.london" }
  ]
}
```

- `platform` is `WORDPRESS` or `BLOGGER`, in any case.
- `syncEveryHours` is optional. It defaults to 24, and the minimum is 1.
- If the configuration is invalid, the server lists every problem on stderr and exits without starting.

| Environment variable | Default | Purpose |
|---|---|---|
| `BLOG_MCP_CONFIG` | `~/.config/blog-mcp/sites.json` | Location of the sites configuration |
| `BLOG_MCP_DATA` | `~/.local/share/blog-mcp` | Where posts, sync checkpoints and the vector index are stored |

## Connect an MCP client

The server uses the stdio transport: the client starts it as a subprocess and talks to it over stdin and
stdout. Any client that supports local stdio servers can use it. Point the client at the jar for the
provider you want:

```sh
java -jar /path/to/blog-mcp-<provider>.jar
```

Set `BLOG_MCP_CONFIG` and `BLOG_MCP_DATA` in the
server's environment if you don't want the defaults.

Many clients take a JSON configuration in this shape. Check your client's documentation for the file's name
and location:

```json
{
  "mcpServers": {
    "blog-mcp": {
      "command": "java",
      "args": ["-jar", "/path/to/blog-mcp-onnx.jar"],
      "env": { "BLOG_MCP_CONFIG": "/path/to/sites.json" }
    }
  }
}
```

For example, with Claude Code:

```sh
claude mcp add --env BLOG_MCP_CONFIG=/path/to/sites.json --transport stdio blog-mcp -- \
  java -jar /path/to/blog-mcp-onnx.jar
```

Keep another option, such as `--transport stdio`, between `--env` and the server name.

### Compatibility

- **Protocol versions:** 2024-11-05, 2025-03-26, 2025-06-18 and 2025-11-25, through the MCP Java SDK 2.0.1. A
  client that asks for any other version is offered 2025-11-25, and the server logs a warning. It never refuses
  the connection.
- **Older clients:** each tool result carries structured JSON (`structuredContent`) and the same JSON as text, so
  clients on 2024-11-05, which predates structured content, can still read it.
- **Requirements:** the client needs the stdio transport and tools support. The server offers no HTTP transport,
  resources or prompts.

## How it runs

- **First start:** the server answers straight away. Search results stay incomplete until the first sync and
  index build finish, which takes from tens of seconds to a few minutes.
- **Later starts:** the stored index is served immediately. A background job then reconciles the index with the
  stored posts, syncs every site, and reconciles again. After that it syncs and reconciles every
  `syncEveryHours`.
- **Interrupted sessions:** posts are saved one at a time, so a session that ends mid-sync keeps every post it
  saved. The opening reconcile of the next session indexes any that were saved but not yet indexed, and the
  next sync carries on fetching the rest.
- **Storage:** one JSON file per post, checkpoint and indexed post, each written atomically. An unreadable file
  is renamed to `*.corrupt` and fetched again rather than crashing the server. If the embedding provider/profile
  changes, the affected entries are re-embedded automatically.
- **Logs** go to stderr, which most clients show in their MCP logs. Stdout carries only the protocol.
- **Exit:** the server exits when the client closes its stdin, or on SIGTERM or SIGINT. It first stops starting
  new embeddings, and waits up to 5 seconds for one already running, so the native model runtime is never
  torn down mid-call.

## Development

Java 25 and Gradle, built with domain-driven design and hexagonal architecture. There are three areas:

- `catalog`: syncing and storing posts
- `search`: chunking, embedding and querying
- `app`: the composition root and the MCP server

| Command | What it does |
|---|---|
| `./gw check` | Compile (`-Werror`), Spotless, tests, ArchUnit layering rules, JaCoCo (≥ 90% on domain and application), PMD complexity budget, SpotBugs and FindSecBugs |
| `./gw pitest` | Mutation testing on the domain packages (≥ 80%) |
| `./gw jarTest` | Runs each provider jar under a real MCP client |
| `./gw spotlessApply` | Format the code |

Use `./gw` instead of `./gradlew`. It is a thin wrapper that also works inside the Claude Code sandbox.

- Design decisions: [`docs/adr/`](docs/adr)
- Domain models, glossaries and acceptance criteria: [`docs/domain/`](docs/domain)
- Contributor and agent rules: [`CLAUDE.md`](CLAUDE.md)

Gradle checks every dependency against `gradle/verification-metadata.xml` (ADR 0008).

## License

[MIT](LICENSE). Bundled dependencies keep their own licenses, all permissive (Apache-2.0, MIT or MIT-0). Provider-specific models and tokenizer assets keep their own licenses and attribution requirements; the selected provider artifact is responsible for preserving those notices.
