package nz.sounie.blogmcp.search.domain.index;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;

/**
 * Aggregate root: one post's metadata, its content fingerprint, its indexed chunks and its indexed
 * images.
 */
public final class IndexedPost {

  private final PostId id;
  private final PostMetadata metadata;
  private final ContentFingerprint fingerprint;
  private final List<IndexedChunk> chunks;
  private final List<IndexedImage> images;

  private IndexedPost(
      PostId id,
      PostMetadata metadata,
      ContentFingerprint fingerprint,
      List<IndexedChunk> chunks,
      List<IndexedImage> images) {
    this.id = Objects.requireNonNull(id, "id");
    this.metadata = Objects.requireNonNull(metadata, "metadata");
    this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
    this.chunks = List.copyOf(chunks);
    this.images = List.copyOf(images);
    requireSameSite();
    requireConsecutiveChunkIndexes();
    requireAscendingImageIndexes();
  }

  /**
   * Builds an indexed post without images from its parts.
   *
   * @throws InvalidIndexedPost as {@link #restore(PostId, PostMetadata, ContentFingerprint, List,
   *     List)}
   */
  public static IndexedPost restore(
      PostId id, PostMetadata metadata, ContentFingerprint fingerprint, List<IndexedChunk> chunks) {
    return restore(id, metadata, fingerprint, chunks, List.of());
  }

  /**
   * Builds an indexed post from its parts. Used by {@link PostIndexer} and to restore stored
   * entries.
   *
   * @throws InvalidIndexedPost if the metadata's site ID is not the post ID's site part, the chunk
   *     indexes are not exactly {@code 0..n-1} in order, or the image indexes are not non-negative
   *     and strictly ascending
   */
  public static IndexedPost restore(
      PostId id,
      PostMetadata metadata,
      ContentFingerprint fingerprint,
      List<IndexedChunk> chunks,
      List<IndexedImage> images) {
    return new IndexedPost(id, metadata, fingerprint, chunks, images);
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

  /** Gaps are allowed: skipped images are not recorded (invariant 7). */
  private void requireAscendingImageIndexes() {
    if (!IntStream.range(0, images.size()).allMatch(i -> images.get(i).index() > indexBefore(i))) {
      throw new InvalidIndexedPost(
          "Image indexes of " + id.external() + " are not non-negative and strictly ascending");
    }
  }

  private int indexBefore(int position) {
    return position == 0 ? -1 : images.get(position - 1).index();
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

  /** A copy with new metadata and the same chunks, images and fingerprint. */
  public IndexedPost withMetadata(PostMetadata metadata) {
    return new IndexedPost(id, metadata, fingerprint, chunks, images);
  }

  /**
   * The best entry over chunks and images ({@link EntryHit#BEST_FIRST}) as a match; empty when the
   * post has neither.
   */
  public Optional<PostMatch> bestMatch(Embedding query) {
    return Stream.<IndexEntry>concat(chunks.stream(), images.stream())
        .map(entry -> entry.hitFor(query))
        .min(EntryHit.BEST_FIRST)
        .map(best -> best.toMatch(id, metadata));
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

  public List<IndexedImage> images() {
    return images;
  }
}
