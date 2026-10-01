package nz.sounie.blogmcp.search.domain;

import java.time.Instant;
import java.util.Set;

/**
 * The catalog's current state of one post, in search language. The single input to every index
 * decision, whether it came from an event or from a reconcile.
 */
public record PostToIndex(
    PostId id, Completeness completeness, PostMetadata metadata, String title, String body) {

  /**
   * Translates a published-language payload. Called by both anti-corruption adapters.
   *
   * @throws MalformedCatalogPost if the post ID is not {@code <siteId>:<sourcePostId>}, the site ID
   *     differs from the post ID's site part, or the completeness is neither {@code FULL} nor
   *     {@code SUMMARY}
   */
  public static PostToIndex of(
      String postId,
      String siteId,
      String canonicalUrl,
      String title,
      String body,
      String completeness,
      Set<String> tags,
      Instant publishedAt,
      Instant updatedAt) {
    throw new UnsupportedOperationException("not implemented");
  }
}
