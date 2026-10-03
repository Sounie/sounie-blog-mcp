package nz.sounie.blogmcp.search.adapter.out;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.index.ContentFingerprint;
import nz.sounie.blogmcp.search.domain.index.IndexRecipe;
import nz.sounie.blogmcp.search.domain.index.IndexedChunk;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.post.SiteId;

/**
 * One indexed post as stored JSON (Jackson 3, {@code "format": 1}), with the model and recipe it
 * was built with. Restoring goes through {@link IndexedPost#restore}, {@link IndexedChunk} and
 * {@link Embedding}, so a value that breaks a domain rule makes the file unreadable.
 */
record IndexFile(
    int format,
    String postId,
    String modelId,
    String recipe,
    String fingerprint,
    Metadata metadata,
    List<Chunk> chunks) {

  static final int CURRENT_FORMAT = 1;

  IndexFile {
    chunks = List.copyOf(chunks);
  }

  /** The post's metadata; instants in ISO-8601 form. */
  record Metadata(
      String siteId,
      String canonicalUrl,
      String title,
      List<String> tags,
      String publishedAt,
      String updatedAt) {

    static Metadata of(PostMetadata metadata) {
      return new Metadata(
          metadata.siteId().value(),
          metadata.canonicalUrl(),
          metadata.title(),
          metadata.tags().stream().sorted().toList(),
          metadata.publishedAt().toString(),
          metadata.updatedAt().toString());
    }

    PostMetadata toPostMetadata() {
      return new PostMetadata(
          new SiteId(siteId),
          canonicalUrl,
          title,
          Set.copyOf(tags),
          Instant.parse(publishedAt),
          Instant.parse(updatedAt));
    }
  }

  /** One chunk; its vector encoded by {@link VectorCodec}. */
  record Chunk(int index, String text, String vector) {

    static Chunk of(IndexedChunk chunk) {
      return new Chunk(chunk.index(), chunk.text(), VectorCodec.encode(chunk.embedding().values()));
    }

    IndexedChunk toIndexedChunk() {
      return new IndexedChunk(index, text, new Embedding(VectorCodec.decode(vector)));
    }
  }

  static IndexFile of(IndexedPost post, String modelId, IndexRecipe recipe) {
    return new IndexFile(
        CURRENT_FORMAT,
        post.id().external(),
        modelId,
        recipe.id(),
        post.fingerprint().value(),
        Metadata.of(post.metadata()),
        post.chunks().stream().map(Chunk::of).toList());
  }

  /**
   * The indexed post this file holds.
   *
   * @throws RuntimeException naming the problem, if a value breaks a domain rule
   */
  IndexedPost toIndexedPost() {
    return IndexedPost.restore(
        PostId.parse(postId),
        metadata.toPostMetadata(),
        new ContentFingerprint(fingerprint),
        chunks.stream().map(Chunk::toIndexedChunk).toList());
  }
}
