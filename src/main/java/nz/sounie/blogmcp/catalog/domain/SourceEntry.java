package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** One item returned by a blog source. */
public sealed interface SourceEntry {

  /** The entry's source timestamp, if it could be read. Counts towards the checkpoint. */
  Optional<Instant> updatedAt();

  /**
   * A public post, with notes about anything the source dropped while mapping it (for example a tag
   * reference that could not be resolved).
   */
  record Available(PostSnapshot snapshot, List<String> notes) implements SourceEntry {

    public Available(PostSnapshot snapshot) {
      this(snapshot, List.of());
    }

    @Override
    public Optional<Instant> updatedAt() {
      return Optional.of(snapshot.updatedAt());
    }
  }

  /** A post that exists but is not public, for example password-protected. */
  record NotPublic(SourcePostId sourcePostId, Optional<Instant> updatedAt) implements SourceEntry {}

  /** An entry that could not be mapped. */
  record Malformed(Optional<SourcePostId> sourcePostId, String reason, Optional<Instant> updatedAt)
      implements SourceEntry {}
}
