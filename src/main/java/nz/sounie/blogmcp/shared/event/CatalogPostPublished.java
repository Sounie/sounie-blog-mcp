package nz.sounie.blogmcp.shared.event;

import java.time.Instant;
import java.util.Set;

/**
 * The catalog added a post. {@code completeness} is {@code FULL} or {@code SUMMARY}; {@code postId}
 * is in the external form {@code <siteId>:<sourcePostId>}.
 */
public record CatalogPostPublished(
    String postId,
    String siteId,
    String canonicalUrl,
    String title,
    String body,
    String completeness,
    Set<String> tags,
    Instant publishedAt,
    Instant updatedAt)
    implements IntegrationEvent {}
