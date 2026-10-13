package nz.sounie.blogmcp.shared.query;

/**
 * One post image in the published language: an absolute {@code https} URL, with alt text and
 * caption that are empty when absent (never null).
 */
public record CatalogPostImage(String url, String altText, String caption) {}
