package nz.sounie.blogmcp.search.domain;

import java.util.List;
import java.util.Optional;

/** Aggregate root: one post's metadata, its content fingerprint and its indexed chunks. */
public final class IndexedPost {

  private IndexedPost() {}

  /**
   * Builds an indexed post from its parts. Used by {@link PostIndexer} and to restore stored
   * entries.
   *
   * @throws InvalidIndexedPost if the metadata's site ID is not the post ID's site part, or the
   *     chunk indexes are not exactly {@code 0..n-1} in order
   */
  public static IndexedPost restore(
      PostId id, PostMetadata metadata, ContentFingerprint fingerprint, List<IndexedChunk> chunks) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** The re-index decision for a {@code FULL} post that is already indexed. */
  public IndexDecision decideFor(PostToIndex post, ContentFingerprint current) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** A copy with new metadata and the same chunks and fingerprint. */
  public IndexedPost withMetadata(PostMetadata metadata) {
    throw new UnsupportedOperationException("not implemented");
  }

  /**
   * The best chunk (ties go to the lower chunk index) as a match; empty when there are no chunks.
   */
  public Optional<PostMatch> bestMatch(Embedding query) {
    throw new UnsupportedOperationException("not implemented");
  }

  public PostId id() {
    throw new UnsupportedOperationException("not implemented");
  }

  public PostMetadata metadata() {
    throw new UnsupportedOperationException("not implemented");
  }

  public ContentFingerprint fingerprint() {
    throw new UnsupportedOperationException("not implemented");
  }

  public List<IndexedChunk> chunks() {
    throw new UnsupportedOperationException("not implemented");
  }
}
