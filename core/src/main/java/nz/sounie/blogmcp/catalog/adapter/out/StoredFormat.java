package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.Optional;
import java.util.Set;

/**
 * The {@code format} versions of one kind of the catalog's stored files: the version this release
 * writes, and every version it still reads.
 */
record StoredFormat(int written, Set<Integer> readable) {

  /** Sync checkpoint files: format 1 only. */
  static final StoredFormat CHECKPOINT = new StoredFormat(1, Set.of(1));

  /** Post files: format 2 adds the post images; format 1 is still read, with none (Q9). */
  static final StoredFormat POST = new StoredFormat(2, Set.of(1, 2));

  StoredFormat {
    readable = Set.copyOf(readable);
  }

  /**
   * @throws IllegalArgumentException if the stored format is missing or not readable
   */
  void require(Integer stored) {
    Optional.ofNullable(stored)
        .filter(readable::contains)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown format " + stored + "; this version reads formats " + readable));
  }
}
