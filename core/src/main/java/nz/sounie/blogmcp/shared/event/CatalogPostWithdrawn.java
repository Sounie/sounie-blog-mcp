package nz.sounie.blogmcp.shared.event;

/**
 * The catalog removed a post. {@code reason} is {@code NO_LONGER_LISTED}, {@code NO_LONGER_PUBLIC}
 * or {@code SITE_REMOVED}.
 */
public record CatalogPostWithdrawn(String postId, String siteId, String canonicalUrl, String reason)
    implements IntegrationEvent {}
