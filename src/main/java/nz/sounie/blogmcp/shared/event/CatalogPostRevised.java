package nz.sounie.blogmcp.shared.event;

import java.time.Instant;
import java.util.Set;

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
    Instant publishedAt,
    Instant updatedAt,
    Set<String> changed)
    implements IntegrationEvent {}
