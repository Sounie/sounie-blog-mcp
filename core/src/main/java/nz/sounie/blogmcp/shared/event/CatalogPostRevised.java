package nz.sounie.blogmcp.shared.event;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import nz.sounie.blogmcp.shared.query.CatalogPostImage;

/**
 * The catalog revised a post materially. {@code changed} holds the names of the revised aspects:
 * {@code TITLE}, {@code BODY}, {@code COMPLETENESS}, {@code TAGS}, {@code URL}, {@code
 * PUBLISHED_AT}.
 */
public record CatalogPostRevised(
    String postId,
    String siteId,
    String canonicalUrl,
    String title,
    String body,
    String completeness,
    Set<String> tags,
    List<CatalogPostImage> images,
    Instant publishedAt,
    Instant updatedAt,
    Set<String> changed)
    implements IntegrationEvent {}
