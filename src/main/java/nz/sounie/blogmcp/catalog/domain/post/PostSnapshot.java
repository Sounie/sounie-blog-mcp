package nz.sounie.blogmcp.catalog.domain.post;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * What a blog source currently says about one post, in catalog language. If {@code updatedAt} is
 * earlier than {@code publishedAt}, it is normalised to {@code publishedAt}. Tags are copied into
 * an unmodifiable set.
 */
public record PostSnapshot(
    PostId id,
    CanonicalUrl url,
    Title title,
    Body body,
    BodyCompleteness completeness,
    Set<Tag> tags,
    Instant publishedAt,
    Instant updatedAt) {

  public PostSnapshot {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(url, "url");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(completeness, "completeness");
    Objects.requireNonNull(publishedAt, "publishedAt");
    Objects.requireNonNull(updatedAt, "updatedAt");
    tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    if (updatedAt.isBefore(publishedAt)) {
      updatedAt = publishedAt;
    }
  }

  /** The material part of the snapshot. */
  public PostContent content() {
    return new PostContent(url, title, body, completeness, tags, publishedAt);
  }
}
