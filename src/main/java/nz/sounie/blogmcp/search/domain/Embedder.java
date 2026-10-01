package nz.sounie.blogmcp.search.domain;

import java.util.List;

/** Port: the local embedding model. */
public interface Embedder {

  /** Part of the index recipe, e.g. {@code bge-small-en-v1.5-q}. */
  String modelId();

  /**
   * One embedding per passage, in order.
   *
   * @throws EmbedderUnavailable if the model cannot embed
   */
  List<Embedding> embedPassages(List<Passage> passages);

  /**
   * @throws EmbedderUnavailable if the model cannot embed
   */
  Embedding embedQuery(QueryPassage query);
}
