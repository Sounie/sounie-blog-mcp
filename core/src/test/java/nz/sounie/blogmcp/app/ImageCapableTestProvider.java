package nz.sounie.blogmcp.app;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbedderShutdown;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/**
 * An implementation of our own {@link EmbeddingProvider} SPI that can embed images, backed by the
 * {@link FakeEmbedder}: the in-process stand-in for the {@code gemma2} provider's capability.
 */
final class ImageCapableTestProvider implements EmbeddingProvider {

  private final FakeEmbedder embedder;

  ImageCapableTestProvider(FakeEmbedder embedder) {
    this.embedder = embedder;
  }

  @Override
  public String modelId() {
    return embedder.modelId();
  }

  @Override
  public List<Embedding> embedPassages(List<Passage> passages) {
    return embedder.embedPassages(passages);
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    return embedder.embedQuery(query);
  }

  @Override
  public EmbeddingProviderProfile profile() {
    return new EmbeddingProviderProfile(
        "fake-revision",
        "fake-revision",
        "fake-image-capable-v1",
        384,
        512,
        new PassageTokenBudget(400, 100, 64, 465, 512),
        input -> input.split("\\s+").length);
  }

  @Override
  public TokenCounter tokenCounter() {
    return FakeTokenCounter.perWord(1);
  }

  @Override
  public String prepareQuery(QueryText query) {
    return query.value();
  }

  @Override
  public String preparePassage(String title, Passage passage) {
    return passage.text();
  }

  @Override
  public String passagePreparationVersion() {
    return "fake-preparation-v1";
  }

  @Override
  public Optional<ImageEmbedder> imageEmbedder() {
    return Optional.of(embedder);
  }

  @Override
  public EmbedderShutdown shutDown(Duration maxWait) {
    return EmbedderShutdown.DRAINED;
  }
}
