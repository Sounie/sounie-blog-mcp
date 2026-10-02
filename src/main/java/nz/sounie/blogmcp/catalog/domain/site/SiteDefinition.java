package nz.sounie.blogmcp.catalog.domain.site;

/**
 * One site exactly as written in the sites configuration, before validation. {@link
 * SitesConfiguration#of} turns a list of these into validated {@link Site}s.
 */
public record SiteDefinition(String id, String platform, String baseUrl) {}
