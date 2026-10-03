package nz.sounie.blogmcp.search.adapter.out;

/**
 * Reads one index file ({@code IndexFile}, Jackson 3) through {@code IndexedPost.restore}, {@code
 * IndexedChunk} and {@code Embedding}, and decides model compatibility: the one place that compares
 * the stored model ID with the current one.
 */
final class IndexFileLoader {

  IndexFileLoader(String currentModelId) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** Never throws: anything wrong with the content is {@link IndexFileLoad.Unreadable}. */
  IndexFileLoad read(byte[] content) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
