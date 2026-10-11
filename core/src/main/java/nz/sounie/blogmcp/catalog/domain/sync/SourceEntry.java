package nz.sounie.blogmcp.catalog.domain.sync;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.post.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.post.SourcePostId;

/** One item returned by a blog source. */
public sealed interface SourceEntry {

  /** The entry's source timestamp, if it could be read. Counts towards the checkpoint. */
  Optional<Instant> updatedAt();

  /** The entry's source post ID, if it could be read. Only a malformed entry may lack one. */
  Optional<SourcePostId> readableSourcePostId();

  /**
   * A public post, with notes about anything the source dropped while mapping it (for example a tag
   * reference that could not be resolved).
   */
  record Available(PostSnapshot snapshot, List<String> notes) implements SourceEntry {

    public Available {
      Objects.requireNonNull(snapshot, "snapshot");
      notes = List.copyOf(notes);
    }

    public Available(PostSnapshot snapshot) {
      this(snapshot, List.of());
    }

    @Override
    public Optional<Instant> updatedAt() {
      return Optional.of(snapshot.updatedAt());
    }

    @Override
    public Optional<SourcePostId> readableSourcePostId() {
      return Optional.of(snapshot.id().sourcePostId());
    }
  }

  /** A post that exists but is not public, for example password-protected. */
  record NotPublic(SourcePostId sourcePostId, Optional<Instant> updatedAt) implements SourceEntry {

    public NotPublic {
      Objects.requireNonNull(sourcePostId, "source post ID");
      Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override
    public Optional<SourcePostId> readableSourcePostId() {
      return Optional.of(sourcePostId);
    }
  }

  /** An entry that could not be mapped. */
  record Malformed(Optional<SourcePostId> sourcePostId, String reason, Optional<Instant> updatedAt)
      implements SourceEntry {

    public Malformed {
      Objects.requireNonNull(sourcePostId, "source post ID");
      Objects.requireNonNull(reason, "reason");
      Objects.requireNonNull(updatedAt, "updatedAt");
    }

    @Override
    public Optional<SourcePostId> readableSourcePostId() {
      return sourcePostId;
    }
  }
}
