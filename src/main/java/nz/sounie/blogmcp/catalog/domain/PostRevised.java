package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
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
    implements PostEvent {}
