package nz.sounie.blogmcp.search.domain.index;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import nz.sounie.blogmcp.search.domain.image.PostImage;
import nz.sounie.blogmcp.search.domain.post.MalformedCatalogPost;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import nz.sounie.blogmcp.search.domain.text.WordSequence;

/**
 * The catalog's current state of one post, in search language, with its post images in catalog
 * order. The single input to every index decision, whether it came from an event or from a
 * reconcile.
 *
 * <p>The compact constructor throws {@link MalformedCatalogPost} if the metadata's site ID is not
 * the post ID's site part.
 */
public record PostToIndex(
    PostId id,
    Completeness completeness,
    PostMetadata metadata,
    String title,
    String body,
    List<PostImage> images) {

  public PostToIndex {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(completeness, "completeness");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    images = List.copyOf(images);
    if (!metadata.siteId().equals(id.siteId())) {
      throw new MalformedCatalogPost(
          "Site ID " + metadata.siteId().value() + " differs from post ID " + id.external());
    }
  }

  /**
   * Translates a published-language payload. Called by both anti-corruption adapters, which parse
   * the images with {@link PostImage#of}.
   *
   * @throws MalformedCatalogPost if any argument (or tag, or image) is null, the post ID is not
   *     {@code <siteId>:<sourcePostId>}, the site ID differs from the post ID's site part, or the
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
      List<PostImage> images,
      Instant publishedAt,
      Instant updatedAt) {
    return new PostToIndex(
        PostId.parse(postId),
        Completeness.parse(completeness),
        new PostMetadata(
            new SiteId(siteId),
            present(canonicalUrl, "canonical URL"),
            present(title, "title"),
            presentTags(tags),
            present(publishedAt, "published at"),
            present(updatedAt, "updated at")),
        title,
        present(body, "body"),
        presentImages(images));
  }

  private static <T> T present(T value, String field) {
    return MalformedCatalogPost.requirePresent(value, field);
  }

  /** The tags, each present: a null set or a null tag is malformed, not a NullPointerException. */
  private static Set<String> presentTags(Set<String> tags) {
    present(tags, "tags").forEach(tag -> present(tag, "tag"));
    return tags;
  }

  /** The images, each present: a null list or a null image is malformed. */
  private static List<PostImage> presentImages(List<PostImage> images) {
    present(images, "images").forEach(image -> present(image, "image"));
    return images;
  }

  /** The text-only content fingerprint of this post's title and body under the given recipe. */
  public ContentFingerprint fingerprintUnder(IndexRecipe recipe) {
    return ContentFingerprint.of(recipe, title, WordSequence.of(body));
  }
}
