package nz.sounie.blogmcp.search.domain.embedding;

import java.util.List;
import nz.sounie.blogmcp.search.domain.text.Passage;

/** Port: the local embedding model, as the index uses it to embed passages. */
public interface PassageEmbedder {

  /** Part of the index recipe, e.g. {@code bge-small-en-v1.5-q}. */
  String modelId();

  /**
   * One embedding per passage, in order.
   *
   * @throws EmbedderUnavailable if the model cannot embed
   */
  List<Embedding> embedPassages(List<Passage> passages);
}
