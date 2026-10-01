package nz.sounie.blogmcp.catalog.domain;

/**
 * Owner-chosen, stable, lower-case slug that identifies a site: {@code [a-z0-9-]{1,40}}. Rejects
 * anything else with {@link IllegalArgumentException}.
 */
public record SiteId(String value) {}
