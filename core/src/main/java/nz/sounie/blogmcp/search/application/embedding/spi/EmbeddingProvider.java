package nz.sounie.blogmcp.search.application.embedding.spi;

import java.time.Duration;
import java.util.Optional;
import nz.sounie.blogmcp.search.application.QueryEmbedder;
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.embedding.PassageEmbedder;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/** Search-owned provider contract implemented by exactly one selected provider artifact. */
public interface EmbeddingProvider extends PassageEmbedder, QueryEmbedder {

  EmbeddingProviderProfile profile();

  TokenCounter tokenCounter();

  String prepareQuery(QueryText query);

  String preparePassage(String title, Passage passage);

  /**
   * Names this provider's passage preparation for the index recipe. Change it whenever {@link
   * #preparePassage} changes, so existing entries are re-embedded.
   */
  String passagePreparationVersion();

  /**
   * This provider's image embedder, or empty if it is text-only (ADR 0011). Asking never loads a
   * model.
   */
  Optional<ImageEmbedder> imageEmbedder();

  /**
   * Tells the provider the process is exiting: from now on it starts no new model call, and every
   * embed call throws {@link nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable}. Then
   * waits up to {@code maxWait} for the call already running. Never loads a model that was not
   * loaded. Safe to call more than once.
   */
  EmbedderShutdown shutDown(Duration maxWait);
}
