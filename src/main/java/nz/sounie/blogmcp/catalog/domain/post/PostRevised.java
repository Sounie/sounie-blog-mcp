package nz.sounie.blogmcp.catalog.domain.post;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** A post changed materially. Carries the full new state and which aspects changed. */
public record PostRevised(
    PostId postId,
    CanonicalUrl url,
    Title title,
    Body body,
    BodyCompleteness completeness,
    Set<Tag> tags,
    Instant publishedAt,
    Instant updatedAt,
    Set<RevisedAspect> changed)
    implements PostEvent {

  public PostRevised {
    tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    if (changed.isEmpty()) {
      throw new IllegalArgumentException("A revision changes at least one aspect");
    }
    changed = Collections.unmodifiableSet(EnumSet.copyOf(changed));
  }
}
