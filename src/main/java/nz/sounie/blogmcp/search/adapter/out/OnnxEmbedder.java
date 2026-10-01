package nz.sounie.blogmcp.search.adapter.out;

import java.util.List;
import nz.sounie.blogmcp.search.domain.Embedder;
import nz.sounie.blogmcp.search.domain.Embedding;
import nz.sounie.blogmcp.search.domain.Passage;
import nz.sounie.blogmcp.search.domain.QueryPassage;

/**
 * The local BGE-small-en-v1.5 (quantised) model through LangChain4j. The only class that touches
 * LangChain4j. Serialises its public calls with one lock; loads the model lazily on first use.
 */
public final class OnnxEmbedder implements Embedder {

  public static final String MODEL_ID = "bge-small-en-v1.5-q";

  @Override
  public String modelId() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public List<Embedding> embedPassages(List<Passage> passages) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    throw new UnsupportedOperationException("not implemented");
  }
}
