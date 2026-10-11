package nz.sounie.blogmcp.search.application.embedding.spi;

import nz.sounie.blogmcp.search.application.QueryEmbedder;
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
}
