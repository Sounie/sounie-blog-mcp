package nz.sounie.blogmcp.shared.event;

/** An event published by one bounded context for others. Uses JDK types only. */
public sealed interface IntegrationEvent
    permits CatalogPostPublished, CatalogPostRevised, CatalogPostWithdrawn {}
