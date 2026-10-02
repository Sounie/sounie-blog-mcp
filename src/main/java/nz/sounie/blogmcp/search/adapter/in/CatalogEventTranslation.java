package nz.sounie.blogmcp.search.adapter.in;

import nz.sounie.blogmcp.search.domain.index.IndexChange;
import nz.sounie.blogmcp.search.domain.index.PostToIndex;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.shared.event.CatalogPostPublished;
import nz.sounie.blogmcp.shared.event.CatalogPostRevised;
import nz.sounie.blogmcp.shared.event.CatalogPostWithdrawn;
import nz.sounie.blogmcp.shared.event.IntegrationEvent;

/**
 * Anti-corruption layer: catalog integration events to index changes. Publish and revise become
 * {@code Upsert}; withdraw becomes {@code Remove}.
 */
public final class CatalogEventTranslation {

  private CatalogEventTranslation() {}

  /**
   * @throws nz.sounie.blogmcp.search.domain.post.MalformedCatalogPost for an invalid payload
   */
  public static IndexChange toChange(IntegrationEvent event) {
    return switch (event) {
      case CatalogPostPublished published -> upsert(published);
      case CatalogPostRevised revised -> upsert(revised);
      case CatalogPostWithdrawn withdrawn ->
          new IndexChange.Remove(PostId.parse(withdrawn.postId()));
    };
  }

  /** The raw post ID of any catalog event, for logging. */
  static String postIdOf(IntegrationEvent event) {
    return switch (event) {
      case CatalogPostPublished published -> published.postId();
      case CatalogPostRevised revised -> revised.postId();
      case CatalogPostWithdrawn withdrawn -> withdrawn.postId();
    };
  }

  private static IndexChange upsert(CatalogPostPublished e) {
    return new IndexChange.Upsert(
        PostToIndex.of(
            e.postId(),
            e.siteId(),
            e.canonicalUrl(),
            e.title(),
            e.body(),
            e.completeness(),
            e.tags(),
            e.publishedAt(),
            e.updatedAt()));
  }

  /** The {@code changed} set is ignored: the content fingerprint decides (search.md 4.1). */
  private static IndexChange upsert(CatalogPostRevised e) {
    return new IndexChange.Upsert(
        PostToIndex.of(
            e.postId(),
            e.siteId(),
            e.canonicalUrl(),
            e.title(),
            e.body(),
            e.completeness(),
            e.tags(),
            e.publishedAt(),
            e.updatedAt()));
  }
}
