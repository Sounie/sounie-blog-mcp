package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
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
    Instant updatedAt) {}
