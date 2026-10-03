# blog-mcp

A local, read-only [MCP](https://modelcontextprotocol.io) server that lets any MCP client (an AI assistant, IDE or agent) search
and read my blog posts.

It syncs posts from WordPress and Blogger sites, stores them as plain text on disk, and indexes them with a
local embedding model (BGE-small-en-v1.5, quantised ONNX, bundled in the jar). Search runs entirely on your
machine: no API keys, and no calls to hosted models. The only network traffic is fetching the blogs' public
feeds.

## Tools

| Tool | What it does |
|---|---|
| `search_posts` | Semantic search over all configured sites. You can limit it to one `site`, a `from`/`to` publication date range (`yyyy-MM-dd`, New Zealand time) and a `limit` of 1–20 results (default 10). Each result has a title, URL, date, relevance score and snippet, plus a `postId`. |
| `get_post` | Returns one post in full as plain text, looked up by `postId` (e.g. `sounie-wp:123`) or by URL. |

Both tools are marked read-only. Their descriptions tell the model to treat post text as data, never as
instructions.

## Requirements

- Java 25

## Build

```sh
./gw shadowJar
```

This produces `build/libs/blog-mcp-all.jar` (about 130 MB, most of it the embedding model).

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
stdout. Any client that supports local stdio servers can use it. Point the client at this command:

```sh
java -jar /path/to/blog-mcp-all.jar
```

Set `BLOG_MCP_CONFIG` and `BLOG_MCP_DATA` in the server's environment if you don't want the defaults.

Many clients take a JSON configuration in this shape. Check your client's documentation for the file's name
and location:

```json
{
  "mcpServers": {
    "blog-mcp": {
      "command": "java",
      "args": ["-jar", "/path/to/blog-mcp-all.jar"],
      "env": { "BLOG_MCP_CONFIG": "/path/to/sites.json" }
    }
  }
}
```

For example, with Claude Code:

```sh
claude mcp add --env BLOG_MCP_CONFIG=/path/to/sites.json --transport stdio blog-mcp -- \
  java -jar /path/to/blog-mcp-all.jar
```

Keep another option, such as `--transport stdio`, between `--env` and the server name.

## How it runs

- **First start:** the server answers straight away. Search results stay incomplete until the first sync and
  index build finish, which takes from tens of seconds to a few minutes.
- **Later starts:** the stored index is served immediately. A background sync and reconcile job then runs, and
  repeats every `syncEveryHours`.
- **Storage:** one JSON file per post, checkpoint and indexed post, each written atomically. An unreadable file
  is renamed to `*.corrupt` and fetched again rather than crashing the server. If the embedding model changes,
  the affected entries are re-embedded automatically.
- **Logs** go to stderr, which most clients show in their MCP logs. Stdout carries only the protocol.
- The server exits when the client closes its stdin.

## Development

Java 25 and Gradle, built with domain-driven design and hexagonal architecture. There are three areas:

- `catalog`: syncing and storing posts
- `search`: chunking, embedding and querying
- `app`: the composition root and the MCP server

| Command | What it does |
|---|---|
| `./gw check` | Compile (`-Werror`), Spotless, tests, ArchUnit layering rules, JaCoCo (≥ 90% on domain and application), PMD complexity budget, SpotBugs and FindSecBugs |
| `./gw pitest` | Mutation testing on the domain packages (≥ 80%) |
| `./gw spotlessApply` | Format the code |

Use `./gw` instead of `./gradlew`. It is a thin wrapper that also works inside the Claude Code sandbox.

- Design decisions: [`docs/adr/`](docs/adr)
- Domain models, glossaries and acceptance criteria: [`docs/domain/`](docs/domain)
- Contributor and agent rules: [`CLAUDE.md`](CLAUDE.md)

Gradle checks every dependency against `gradle/verification-metadata.xml` (ADR 0008).

## License

[MIT](LICENSE). Bundled dependencies keep their own licenses, all permissive (Apache-2.0, MIT or MIT-0). The
bundled embedding model, [BAAI/bge-small-en-v1.5](https://huggingface.co/BAAI/bge-small-en-v1.5), is MIT.
