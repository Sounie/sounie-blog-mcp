package nz.sounie.blogmcp.search.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Whether the catalog has the full post text or only a summary. Owns indexability: {@code FULL}
 * posts go through the normal index decision; {@code SUMMARY} posts are always excluded.
 */
public enum Completeness {
  FULL {
    @Override
    public IndexDecision decide(
        Optional<IndexedPost> existing, PostToIndex post, ContentFingerprint current) {
      return existing
          .map(entry -> entry.decideFor(post, current))
          .orElseGet(() -> IndexDecision.forAbsent(post));
    }
  },
  SUMMARY {
    @Override
    public IndexDecision decide(
        Optional<IndexedPost> existing, PostToIndex post, ContentFingerprint current) {
      return new IndexDecision.Exclude(post.id());
    }
  };

  /** Reached only through {@link IndexDecision#forPost}. */
  public abstract IndexDecision decide(
      Optional<IndexedPost> existing, PostToIndex post, ContentFingerprint current);

  /**
   * @throws MalformedCatalogPost if the text is neither {@code FULL} nor {@code SUMMARY}
   */
  public static Completeness parse(String text) {
    return Arrays.stream(values())
        .filter(completeness -> completeness.name().equals(text))
        .findFirst()
        .orElseThrow(
            () -> new MalformedCatalogPost("Completeness is not FULL or SUMMARY: '" + text + "'"));
  }
}
