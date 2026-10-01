package nz.sounie.blogmcp.search.domain;

/**
 * Domain service that creates {@link IndexedPost}s: word sequence, chunking, passage composition,
 * then one {@link Embedder#embedPassages} call for all of the post's passages.
 */
public final class PostIndexer {

  public PostIndexer(
      ChunkingPolicy chunking,
      PassageComposition composition,
      TokenCounter tokens,
      Embedder embedder) {}

  /** The current index recipe: model ID, chunking parameters and composition version. */
  public IndexRecipe recipe() {
    throw new UnsupportedOperationException("not implemented");
  }

  /** The content fingerprint of the post under the current recipe. */
  public ContentFingerprint fingerprintOf(PostToIndex post) {
    throw new UnsupportedOperationException("not implemented");
  }

  /**
   * @throws EmbedderUnavailable if the embedder fails; nothing has been changed by then
   */
  public IndexedPost index(PostToIndex post) {
    throw new UnsupportedOperationException("not implemented");
  }
}
