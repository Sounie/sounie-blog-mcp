package nz.sounie.blogmcp.search.adapter.out;

import java.util.List;
import java.util.Objects;
import nz.sounie.blogmcp.search.application.PostCatalog;
import nz.sounie.blogmcp.search.domain.CatalogEntry;
import nz.sounie.blogmcp.search.domain.MalformedCatalogPost;
import nz.sounie.blogmcp.search.domain.PostId;
import nz.sounie.blogmcp.search.domain.PostToIndex;
import nz.sounie.blogmcp.shared.query.CatalogPostState;
import nz.sounie.blogmcp.shared.query.CatalogPosts;

/**
 * Anti-corruption layer: the shared {@link CatalogPosts} query as search's {@link PostCatalog}.
 *
 * <p>Every state becomes a {@link CatalogEntry}; none is ever dropped (AC-SRCH-38). A malformed
 * state is {@code Unreadable} when its post ID can still be read, and {@code Unidentified} when it
 * cannot. The reconcile report carries the reasons.
 */
public final class SharedCatalogPosts implements PostCatalog {

  private final CatalogPosts catalog;

  public SharedCatalogPosts(CatalogPosts catalog) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
  }

  @Override
  public List<CatalogEntry> currentPosts() {
    return catalog.currentPosts().stream().map(SharedCatalogPosts::entryFor).toList();
  }

  private static CatalogEntry entryFor(CatalogPostState state) {
    try {
      return new CatalogEntry.Readable(translated(state));
    } catch (MalformedCatalogPost e) {
      return malformed(state, e.getMessage());
    }
  }

  /** Unreadable when the post ID still parses, otherwise unidentified. */
  private static CatalogEntry malformed(CatalogPostState state, String reason) {
    try {
      return new CatalogEntry.Unreadable(PostId.parse(state.postId()), reason);
    } catch (MalformedCatalogPost e) {
      return new CatalogEntry.Unidentified(reason);
    }
  }

  private static PostToIndex translated(CatalogPostState state) {
    return PostToIndex.of(
        state.postId(),
        state.siteId(),
        state.canonicalUrl(),
        state.title(),
        state.body(),
        state.completeness(),
        state.tags(),
        state.publishedAt(),
        state.updatedAt());
  }
}
