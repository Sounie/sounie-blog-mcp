package nz.sounie.blogmcp.search.adapter.out;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.application.PostCatalog;
import nz.sounie.blogmcp.search.domain.CatalogEntry;
import nz.sounie.blogmcp.search.domain.MalformedCatalogPost;
import nz.sounie.blogmcp.search.domain.PostToIndex;
import nz.sounie.blogmcp.shared.query.CatalogPostState;
import nz.sounie.blogmcp.shared.query.CatalogPosts;

/**
 * Anti-corruption layer: the shared {@link CatalogPosts} query as search's {@link PostCatalog}.
 *
 * <p>A state that search cannot translate is logged to the error stream and skipped, so one bad
 * post never aborts a reconcile. Being absent from the catalog's posts, any entry it has in the
 * index is then removed as an orphan.
 */
public final class SharedCatalogPosts implements PostCatalog {

  private final CatalogPosts catalog;
  private final PrintStream errors;

  /** Logs untranslatable posts to {@link System#err}; stdout is reserved for the MCP protocol. */
  public SharedCatalogPosts(CatalogPosts catalog) {
    this(catalog, System.err);
  }

  public SharedCatalogPosts(CatalogPosts catalog, PrintStream errors) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.errors = Objects.requireNonNull(errors, "errors");
  }

  @Override
  public List<CatalogEntry> currentPosts() {
    // Compile shim from the red step (fix loop 1): still skips malformed states. The implementer
    // maps them to CatalogEntry.Unreadable / Unidentified (AC-SRCH-38).
    return catalog.currentPosts().stream()
        .flatMap(this::translated)
        .<CatalogEntry>map(CatalogEntry.Readable::new)
        .toList();
  }

  private Stream<PostToIndex> translated(CatalogPostState state) {
    try {
      return Stream.of(
          PostToIndex.of(
              state.postId(),
              state.siteId(),
              state.canonicalUrl(),
              state.title(),
              state.body(),
              state.completeness(),
              state.tags(),
              state.publishedAt(),
              state.updatedAt()));
    } catch (MalformedCatalogPost e) {
      errors.println(
          "search: skipped catalog post " + state.postId() + " in reconcile: " + e.getMessage());
      return Stream.empty();
    }
  }
}
