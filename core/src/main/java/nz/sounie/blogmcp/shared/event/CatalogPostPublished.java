package nz.sounie.blogmcp.shared.event;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import nz.sounie.blogmcp.shared.query.CatalogPostImage;

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
    List<CatalogPostImage> images,
    Instant publishedAt,
    Instant updatedAt)
    implements IntegrationEvent {}
