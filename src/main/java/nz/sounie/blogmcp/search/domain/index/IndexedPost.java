package nz.sounie.blogmcp.search.domain.index;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;

/** Aggregate root: one post's metadata, its content fingerprint and its indexed chunks. */
public final class IndexedPost {

  private final PostId id;
  private final PostMetadata metadata;
  private final ContentFingerprint fingerprint;
  private final List<IndexedChunk> chunks;

  private IndexedPost(
      PostId id, PostMetadata metadata, ContentFingerprint fingerprint, List<IndexedChunk> chunks) {
    this.id = Objects.requireNonNull(id, "id");
    this.metadata = Objects.requireNonNull(metadata, "metadata");
    this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    this.chunks = List.copyOf(chunks);
    requireSameSite();
    requireConsecutiveChunkIndexes();
  }

  /**
   * Builds an indexed post from its parts. Used by {@link PostIndexer} and to restore stored
   * entries.
   *
   * @throws InvalidIndexedPost if the metadata's site ID is not the post ID's site part, or the
   *     chunk indexes are not exactly {@code 0..n-1} in order
   */
  public static IndexedPost restore(
      PostId id, PostMetadata metadata, ContentFingerprint fingerprint, List<IndexedChunk> chunks) {
    return new IndexedPost(id, metadata, fingerprint, chunks);
  }

  private void requireSameSite() {
    if (!metadata.siteId().equals(id.siteId())) {
      throw new InvalidIndexedPost(
          "Site ID " + metadata.siteId().value() + " differs from post ID " + id.external());
    }
  }

  private void requireConsecutiveChunkIndexes() {
    if (!IntStream.range(0, chunks.size()).allMatch(i -> chunks.get(i).index() == i)) {
      throw new InvalidIndexedPost("Chunk indexes of " + id.external() + " are not 0..n-1");
    }
  }

  /** The re-index decision for a {@code FULL} post that is already indexed. */
  public IndexDecision decideFor(PostToIndex post, ContentFingerprint current) {
    if (!fingerprint.equals(current)) {
      return new IndexDecision.ReEmbed(post);
    }
    return metadata.equals(post.metadata())
        ? new IndexDecision.Keep(id)
        : new IndexDecision.RefreshMetadata(this, post.metadata());
  }

  /** A copy with new metadata and the same chunks and fingerprint. */
  public IndexedPost withMetadata(PostMetadata metadata) {
    return new IndexedPost(id, metadata, fingerprint, chunks);
  }

  /**
   * The best chunk (ties go to the lower chunk index) as a match; empty when there are no chunks.
   */
  public Optional<PostMatch> bestMatch(Embedding query) {
    return chunks.stream()
        .map(chunk -> chunk.hitFor(query))
        .min(ChunkHit.BEST_FIRST)
        .map(best -> new PostMatch(id, metadata, best.similarity(), Snippet.of(best.text())));
  }

  public PostId id() {
    return id;
  }

  public PostMetadata metadata() {
    return metadata;
  }

  public ContentFingerprint fingerprint() {
    return fingerprint;
  }

  public List<IndexedChunk> chunks() {
    return chunks;
  }
}
