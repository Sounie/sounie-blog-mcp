package nz.sounie.blogmcp.search.adapter.out;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.index.IndexRecipe;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.post.PostId;

/**
 * The vector index on disk: one JSON file per post at {@code
 * <data>/search/index/<siteId>/<FileKey(postId external form)>.json}, whose vectors are base64
 * little-endian float32 ({@link VectorCodec}). Every file is loaded when opened; then the index is
 * write-through, with lookups served from memory. {@link IndexedPost} is immutable, so entries are
 * shared as they are.
 *
 * <p>On open, an unreadable file is quarantined and logged; a file built with a different model is
 * deleted and logged ({@link IndexFileLoader}). The next reconcile re-adds both.
 */
public final class FileVectorIndex implements VectorIndex {

  private FileVectorIndex() {}

  /**
   * Loads every compatible index file under the data directory.
   *
   * @param modelId the current model ID ({@code PassageEmbedder.modelId()}); written to each file
   *     and compared on load
   * @param recipe the current index recipe, written to each file
   */
  public static FileVectorIndex open(
      Path dataDirectory, String modelId, IndexRecipe recipe, PrintStream errors) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public Optional<IndexedPost> find(PostId id) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public void save(IndexedPost post) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public boolean remove(PostId id) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public Stream<IndexedPost> all() {
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public Set<PostId> ids() {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
