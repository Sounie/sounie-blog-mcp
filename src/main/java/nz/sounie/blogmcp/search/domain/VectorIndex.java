package nz.sounie.blogmcp.search.domain;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/** Port: every indexed post. The repository of {@link IndexedPost}. */
public interface VectorIndex {

  Optional<IndexedPost> find(PostId id);

  /** Atomically replaces any entry for the same post ID. */
  void save(IndexedPost post);

  /** Whether an entry was removed. */
  boolean remove(PostId id);

  /** A consistent snapshot per post. */
  Stream<IndexedPost> all();

  Set<PostId> ids();
}
