package nz.sounie.blogmcp.search.adapter.out;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.index.ContentFingerprint;
import nz.sounie.blogmcp.search.domain.index.IndexRecipe;
import nz.sounie.blogmcp.search.domain.index.IndexedChunk;
import nz.sounie.blogmcp.search.domain.index.IndexedImage;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.post.SiteId;

/**
 * One indexed post as stored JSON (Jackson 3, {@code "format": 2}), with the model and recipe it
 * was built with. Format 2 adds {@code images} to format 1; a format-1 file has none. Restoring
 * goes through {@link IndexedPost#restore}, {@link IndexedChunk}, {@link IndexedImage} and {@link
 * Embedding}, so a value that breaks a domain rule makes the file unreadable.
 */
record IndexFile(
    int format,
    String postId,
    String modelId,
    String recipe,
    String fingerprint,
    Metadata metadata,
    List<Chunk> chunks,
    List<Image> images) {

  static final int CURRENT_FORMAT = 2;

  /** Format 1 (before images were indexed) is still read, as having no images. */
  static final Set<Integer> READABLE_FORMATS = Set.of(1, CURRENT_FORMAT);

  IndexFile {
    chunks = List.copyOf(chunks);
    images = Optional.ofNullable(images).map(List::copyOf).orElse(List.of());
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

  /** A stored entry with a vector encoded by {@link VectorCodec}. */
  sealed interface StoredEntry {

    int index();

    String vector();

    /** Names the entry in a reason, e.g. {@code Chunk 3}. */
    String label();

    default Embedding embedding() {
      float[] values = VectorCodec.decode(vector());
      return new Embedding(values.length, values);
    }
  }

  /** One chunk. */
  record Chunk(int index, String text, String vector) implements StoredEntry {

    static Chunk of(IndexedChunk chunk) {
      return new Chunk(chunk.index(), chunk.text(), VectorCodec.encode(chunk.embedding().values()));
    }

    IndexedChunk toIndexedChunk() {
      return new IndexedChunk(index, text, embedding());
    }

    @Override
    public String label() {
      return "Chunk " + index;
    }
  }

  /** One indexed image. */
  record Image(int index, String url, String description, String vector) implements StoredEntry {

    static Image of(IndexedImage image) {
      return new Image(
          image.index(),
          image.url().toString(),
          image.description(),
          VectorCodec.encode(image.embedding().values()));
    }

    IndexedImage toIndexedImage() {
      return new IndexedImage(index, URI.create(url), description, embedding());
    }

    @Override
    public String label() {
      return "Image " + index;
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
        post.chunks().stream().map(Chunk::of).toList(),
        post.images().stream().map(Image::of).toList());
  }

  /** Every stored vector: the chunks', then the images'. */
  Stream<StoredEntry> entries() {
    return Stream.concat(chunks.stream(), images.stream());
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
        chunks.stream().map(Chunk::toIndexedChunk).toList(),
        images.stream().map(Image::toIndexedImage).toList());
  }
}
