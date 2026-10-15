package nz.sounie.blogmcp.app;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import nz.sounie.blogmcp.catalog.adapter.out.BlogSources;
import nz.sounie.blogmcp.catalog.application.BlogSource;
import nz.sounie.blogmcp.catalog.domain.site.Platform;
import nz.sounie.blogmcp.search.adapter.out.HttpImageSource;
import nz.sounie.blogmcp.search.application.QueryEmbedder;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.embedding.PassageEmbedder;
import nz.sounie.blogmcp.search.domain.image.ImageSource;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.PassageFormatter;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/**
 * The outbound adapters that reach outside the process (the blogs, the local model) and the clock.
 * The file repositories are not here: {@link Wiring} opens them on the data directory.
 *
 * @param imageEmbedder the provider's image embedder, or empty for a text-only provider (ADR 0011)
 * @param postImages where post images come from, given the HTTP image source; production uses it as
 *     is. Applied only for an image-capable provider, so a text-only distribution never builds one
 *     (search.md 3.7).
 */
public record Adapters(
    Map<Platform, BlogSource> sources,
    PassageEmbedder passageEmbedder,
    QueryEmbedder queryEmbedder,
    TokenCounter tokenCounter,
    Clock clock,
    EmbeddingProviderProfile profile,
    Function<QueryText, QueryPassage> queryPreparation,
    PassageFormatter passageFormatter,
    String passagePreparationVersion,
    Optional<ImageEmbedder> imageEmbedder,
    Function<HttpImageSource, ImageSource> postImages) {

  /** Post images over HTTP, as in production. */
  private static final Function<HttpImageSource, ImageSource> OVER_HTTP = http -> http;

  /** Text-only: no image embedder. */
  public Adapters(
      Map<Platform, BlogSource> sources,
      PassageEmbedder passageEmbedder,
      QueryEmbedder queryEmbedder,
      TokenCounter tokenCounter,
      Clock clock) {
    this(
        sources,
        passageEmbedder,
        queryEmbedder,
        tokenCounter,
        clock,
        legacyProfile(tokenCounter),
        QueryPassage::of,
        PassageFormatter.standard(),
        "canonical-v1",
        Optional.empty(),
        OVER_HTTP);
  }

  /**
   * The same adapters, with post images from this source instead of HTTP (for example, a fake of
   * our {@link ImageSource} port). Query images still come over HTTP or from local files.
   */
  public Adapters withPostImages(ImageSource source) {
    return new Adapters(
        sources,
        passageEmbedder,
        queryEmbedder,
        tokenCounter,
        clock,
        profile,
        queryPreparation,
        passageFormatter,
        passagePreparationVersion,
        imageEmbedder,
        http -> source);
  }

  /**
   * The real blog sources over the JDK HTTP client, the selected provider instance (from {@link
   * EmbeddingProviderDiscovery}), the tokenizer and the system clock.
   */
  public static Adapters production(EmbeddingProvider provider) {
    return withProvider(BlogSources.overHttp(), provider, java.time.Clock.systemUTC());
  }

  static Adapters withProvider(
      Map<Platform, BlogSource> sources, EmbeddingProvider provider, Clock clock) {
    EmbeddingProviderProfile profile = provider.profile();
    return new Adapters(
        sources,
        provider,
        provider,
        provider.tokenCounter(),
        clock,
        profile,
        query -> new QueryPassage(provider.prepareQuery(query)),
        (title, content) -> new Passage(provider.preparePassage(title, new Passage(content))),
        provider.passagePreparationVersion(),
        provider.imageEmbedder(),
        OVER_HTTP);
  }

  private static EmbeddingProviderProfile legacyProfile(TokenCounter tokens) {
    PassageTokenBudget budget = new PassageTokenBudget(400, 100, 64, 465, 512);
    return new EmbeddingProviderProfile(
        "onnx-bge-small-en-v1.5-q-v1",
        "onnx-bge-small-en-v1.5-q-v1",
        "onnx-bge-small-en-v1.5-q-v1",
        384,
        512,
        budget,
        tokens::tokensIn);
  }
}
