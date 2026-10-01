package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import nz.sounie.blogmcp.catalog.domain.BlogSource;
import nz.sounie.blogmcp.catalog.domain.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.HtmlToText;
import nz.sounie.blogmcp.catalog.domain.PageCursor;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SourcePage;

/**
 * Reads posts from the WordPress REST API ({@code /wp-json/wp/v2/posts}, ordered by modification
 * time ascending) and resolves tag IDs through {@code /wp-json/wp/v2/tags}. Categories are never
 * requested. The cursor is the WordPress page number.
 */
public final class WordPressSource implements BlogSource {

  private final HttpClient httpClient;
  private final HtmlToText htmlToText;
  private final Function<Site, URI> apiBase;

  /** Calls the API under each site's base URL. */
  public WordPressSource(HttpClient httpClient, HtmlToText htmlToText) {
    this(httpClient, htmlToText, Site::baseUrl);
  }

  /**
   * @param apiBase where to send requests for a site (the site's base URL in production; a local
   *     stub server in tests)
   */
  public WordPressSource(
      HttpClient httpClient, HtmlToText htmlToText, Function<Site, URI> apiBase) {
    this.httpClient = httpClient;
    this.htmlToText = htmlToText;
    this.apiBase = apiBase;
  }

  @Override
  public ChangeOrder changeOrder() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public SourcePage fetch(Site site, Optional<Instant> changedSince, PageCursor cursor) {
    throw new UnsupportedOperationException("not implemented");
  }
}
