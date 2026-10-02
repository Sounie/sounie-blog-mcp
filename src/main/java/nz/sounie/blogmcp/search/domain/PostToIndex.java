package nz.sounie.blogmcp.search.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * The catalog's current state of one post, in search language. The single input to every index
 * decision, whether it came from an event or from a reconcile.
 *
 * <p>The compact constructor throws {@link MalformedCatalogPost} if the metadata's site ID is not
 * the post ID's site part.
 */
public record PostToIndex(
    PostId id, Completeness completeness, PostMetadata metadata, String title, String body) {

  public PostToIndex {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(completeness, "completeness");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    if (!metadata.siteId().equals(id.siteId())) {
      throw new MalformedCatalogPost(
          "Site ID " + metadata.siteId().value() + " differs from post ID " + id.external());
    }
  }

  /**
   * Translates a published-language payload. Called by both anti-corruption adapters.
   *
   * @throws MalformedCatalogPost if any argument is null, the post ID is not {@code
   *     <siteId>:<sourcePostId>}, the site ID differs from the post ID's site part, or the
   *     completeness is neither {@code FULL} nor {@code SUMMARY}
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
    return new PostToIndex(
        PostId.parse(postId),
        Completeness.parse(completeness),
        new PostMetadata(
            new SiteId(siteId),
            present(canonicalUrl, "canonical URL"),
            present(title, "title"),
            present(tags, "tags"),
            present(publishedAt, "published at"),
            present(updatedAt, "updated at")),
        title,
        present(body, "body"));
  }

  private static <T> T present(T value, String field) {
    return MalformedCatalogPost.requirePresent(value, field);
  }

  /** The content fingerprint of this post's title and body under the given recipe. */
  public ContentFingerprint fingerprintUnder(IndexRecipe recipe) {
    return ContentFingerprint.of(recipe, title, WordSequence.of(body));
  }
}
