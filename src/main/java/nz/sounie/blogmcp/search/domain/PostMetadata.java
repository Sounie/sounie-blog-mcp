package nz.sounie.blogmcp.search.domain;

import java.time.Instant;
import java.util.Set;

/**
 * Everything about an indexed post that search stores but does not embed. A difference in metadata
 * alone never triggers re-embedding.
 */
public record PostMetadata(
    SiteId siteId,
    String canonicalUrl,
    String title,
    Set<String> tags,
    Instant publishedAt,
    Instant updatedAt) {}
