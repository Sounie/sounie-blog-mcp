package nz.sounie.blogmcp.search.domain.embedding;

/**
 * Port: turns image pixels into an embedding in the same vector space as the provider's query and
 * passage embeddings. Images take no task prefix.
 */
public interface ImageEmbedder {

  /**
   * The embedding of one image.
   *
   * @throws EmbedderUnavailable if the model cannot embed
   */
  Embedding embedImage(ImagePixels pixels);
}
