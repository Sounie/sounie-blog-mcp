package nz.sounie.blogmcp.catalog.domain;

import java.util.Objects;
import java.util.function.Function;

/**
 * A material part of a post that a revision changed. Each aspect knows how to read itself from
 * {@link PostContent}, so comparing two contents needs no conditionals at the call site.
 */
public enum RevisedAspect {
  TITLE(PostContent::title),
  BODY(PostContent::body),
  COMPLETENESS(PostContent::completeness),
  /** Compared as a set of tags, so neither order nor case matters. */
  TAGS(PostContent::tags),
  URL(PostContent::url),
  PUBLISHED_AT(PostContent::publishedAt);

  private final Function<PostContent, Object> valueIn;

  RevisedAspect(Function<PostContent, Object> valueIn) {
    this.valueIn = valueIn;
  }

  /** Whether this aspect has a different value in the two contents. */
  boolean differsBetween(PostContent current, PostContent proposed) {
    return !Objects.equals(valueIn.apply(current), valueIn.apply(proposed));
  }
}
