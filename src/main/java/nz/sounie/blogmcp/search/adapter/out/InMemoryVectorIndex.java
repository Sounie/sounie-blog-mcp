package nz.sounie.blogmcp.search.adapter.out;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.IndexedPost;
import nz.sounie.blogmcp.search.domain.PostId;
import nz.sounie.blogmcp.search.domain.VectorIndex;

/** The vector index in memory, keyed by post ID. File persistence is slice 3. */
public final class InMemoryVectorIndex implements VectorIndex {

  @Override
  public Optional<IndexedPost> find(PostId id) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public void save(IndexedPost post) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public boolean remove(PostId id) {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public Stream<IndexedPost> all() {
    throw new UnsupportedOperationException("not implemented");
  }

  @Override
  public Set<PostId> ids() {
    throw new UnsupportedOperationException("not implemented");
  }
}
