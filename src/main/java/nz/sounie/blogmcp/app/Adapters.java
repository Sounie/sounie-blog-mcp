package nz.sounie.blogmcp.app;

import java.time.Clock;
import java.util.Map;
import nz.sounie.blogmcp.catalog.adapter.out.BlogSources;
import nz.sounie.blogmcp.catalog.application.BlogSource;
import nz.sounie.blogmcp.catalog.domain.site.Platform;
import nz.sounie.blogmcp.search.application.QueryEmbedder;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.domain.embedding.PassageEmbedder;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/**
 * The outbound adapters that reach outside the process (the blogs, the local model) and the clock.
 * The file repositories are not here: {@link Wiring} opens them on the data directory.
 */
public record Adapters(
    Map<Platform, BlogSource> sources,
    PassageEmbedder passageEmbedder,
    QueryEmbedder queryEmbedder,
    TokenCounter tokenCounter,
    Clock clock) {

  /**
   * The real blog sources over the JDK HTTP client, the selected provider instance, the tokenizer
   * and the system clock.
   */
  public static Adapters production() {
    EmbeddingProvider provider =
        EmbeddingProviderDiscovery.discover(Thread.currentThread().getContextClassLoader());
    return new Adapters(
        BlogSources.overHttp(),
        provider,
        provider,
        provider.tokenCounter(),
        java.time.Clock.systemUTC());
  }
}
