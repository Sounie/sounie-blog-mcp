package nz.sounie.blogmcp.app;

import java.time.Clock;
import java.util.Map;
import nz.sounie.blogmcp.catalog.adapter.out.BlogSources;
import nz.sounie.blogmcp.catalog.application.BlogSource;
import nz.sounie.blogmcp.catalog.domain.site.Platform;
import nz.sounie.blogmcp.search.adapter.out.BgeTokenCounter;
import nz.sounie.blogmcp.search.adapter.out.Gemma2Embedder;
import nz.sounie.blogmcp.search.application.QueryEmbedder;
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
   * The real blog sources over the JDK HTTP client, the embedder, the tokenizer and the
   * system clock.
   */
  public static Adapters production() {
//    OnnxEmbedder model = new OnnxEmbedder();
    Gemma2Embedder model = new Gemma2Embedder();
    return new Adapters(
        BlogSources.overHttp(), model, model, new BgeTokenCounter(), Clock.systemUTC());
  }
}
