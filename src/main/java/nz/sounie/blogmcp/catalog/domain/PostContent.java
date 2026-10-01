package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The material, publicly visible part of a post: everything whose change is a revision. Excludes
 * the identity and {@code updatedAt}.
 */
public record PostContent(
    CanonicalUrl url,
    Title title,
    Body body,
    BodyCompleteness completeness,
    Set<Tag> tags,
    Instant publishedAt) {

  public PostContent {
    Objects.requireNonNull(url, "url");
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(completeness, "completeness");
    Objects.requireNonNull(publishedAt, "publishedAt");
    tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags));
  }

  /** The aspects in which the other content differs from this one; empty if none. */
  public Set<RevisedAspect> changedAspects(PostContent other) {
    return Arrays.stream(RevisedAspect.values())
        .filter(aspect -> aspect.differsBetween(this, other))
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(RevisedAspect.class)));
  }
}
