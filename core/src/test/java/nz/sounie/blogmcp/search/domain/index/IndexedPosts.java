package nz.sounie.blogmcp.search.domain.index;

import java.util.List;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.post.PostId;

/** Builds indexed posts directly, with chosen chunk embeddings, for ranking and decision tests. */
public final class IndexedPosts {

  private IndexedPosts() {}

  /** Chunk {@code i} has text {@code "<postId> chunk <i>"} and the i-th embedding. */
  public static IndexedPost indexed(
      PostToIndex post, ContentFingerprint fingerprint, Embedding... chunkEmbeddings) {
    List<IndexedChunk> chunks =
        IntStream.range(0, chunkEmbeddings.length)
            .mapToObj(i -> new IndexedChunk(i, chunkText(post.id(), i), chunkEmbeddings[i]))
            .toList();
    return IndexedPost.restore(post.id(), post.metadata(), fingerprint, chunks);
  }

  public static String chunkText(PostId id, int index) {
    return id.siteId().value() + ":" + id.sourcePostId() + " chunk " + index;
  }

  public static ContentFingerprint fingerprint(char c) {
    return new ContentFingerprint(String.valueOf(c).repeat(64));
  }
}
