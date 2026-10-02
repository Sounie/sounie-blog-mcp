package nz.sounie.blogmcp.search.domain.query;

import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;

/** Port: the local embedding model, as search uses it to embed a query. */
public interface QueryEmbedder {

  /**
   * @throws EmbedderUnavailable if the model cannot embed
   */
  Embedding embedQuery(QueryPassage query);
}
