package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** A post was added to the catalog. Carries the full post state. */
public record PostPublished(
    PostId postId,
    CanonicalUrl url,
    Title title,
    Body body,
    BodyCompleteness completeness,
    Set<Tag> tags,
    Instant publishedAt,
    Instant updatedAt)
    implements PostEvent {

  public PostPublished {
    tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
  }
}
