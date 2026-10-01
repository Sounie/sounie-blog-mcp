package nz.sounie.blogmcp.catalog.application;

import java.time.Instant;
import java.util.List;
import nz.sounie.blogmcp.catalog.domain.BodyCompleteness;

/** Read model of one post, as returned by {@link GetPost}. */
public record PostView(
    String postId,
    String siteId,
    String title,
    String canonicalUrl,
    List<String> tags,
    Instant publishedAt,
    Instant updatedAt,
    String body,
    BodyCompleteness completeness) {}
