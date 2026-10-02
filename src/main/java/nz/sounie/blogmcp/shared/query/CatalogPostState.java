package nz.sounie.blogmcp.shared.query;

import java.time.Instant;
import java.util.Set;

/**
 * The catalog's current state of one stored post. Same fields as {@code CatalogPostPublished}:
 * {@code completeness} is {@code FULL} or {@code SUMMARY}; {@code postId} is in the external form
 * {@code <siteId>:<sourcePostId>}.
 */
public record CatalogPostState(
    String postId,
    String siteId,
    String canonicalUrl,
    String title,
    String body,
    String completeness,
    Set<String> tags,
    Instant publishedAt,
    Instant updatedAt) {}
