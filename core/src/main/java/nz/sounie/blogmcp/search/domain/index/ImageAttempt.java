package nz.sounie.blogmcp.search.domain.index;

import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.search.domain.image.PostImage;

/**
 * What indexing one post image came to. A settled image (indexed or skipped) is final for this
 * content and goes into the stored fingerprint; a pending one is retried by the next upsert.
 */
public sealed interface ImageAttempt {

  /** The index entry this attempt produced, if any. */
  Optional<IndexedImage> entry();

  /** The post image, if its fate is final for this content. */
  Optional<PostImage> settled();

  /** Fetched and embedded. */
  record Indexed(PostImage image, IndexedImage indexed) implements ImageAttempt {
    public Indexed {
      Objects.requireNonNull(image, "image");
      Objects.requireNonNull(indexed, "indexed");
    }

    @Override
    public Optional<IndexedImage> entry() {
      return Optional.of(indexed);
    }

    @Override
    public Optional<PostImage> settled() {
      return Optional.of(image);
    }
  }

  /** Rejected: it can never be indexed, so it is settled without an entry. */
  record Skipped(PostImage image) implements ImageAttempt {
    public Skipped {
      Objects.requireNonNull(image, "image");
    }

    @Override
    public Optional<IndexedImage> entry() {
      return Optional.empty();
    }

    @Override
    public Optional<PostImage> settled() {
      return Optional.of(image);
    }
  }

  /** Unavailable, or its embedding failed: neither recorded nor settled. */
  record Pending(PostImage image) implements ImageAttempt {
    public Pending {
      Objects.requireNonNull(image, "image");
    }

    @Override
    public Optional<IndexedImage> entry() {
      return Optional.empty();
    }

    @Override
    public Optional<PostImage> settled() {
      return Optional.empty();
    }
  }
}
