package nz.sounie.blogmcp.search.adapter.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.post.PostId;

/**
 * The vector index in memory, keyed by post ID. Each entry is an immutable {@link IndexedPost}
 * replaced as a whole, so readers always see one consistent version of a post. File persistence is
 * slice 3.
 */
public final class InMemoryVectorIndex implements VectorIndex {

  private final Map<PostId, IndexedPost> posts = new ConcurrentHashMap<>();

  @Override
  public Optional<IndexedPost> find(PostId id) {
    return Optional.ofNullable(posts.get(id));
  }

  @Override
  public void save(IndexedPost post) {
    posts.put(post.id(), post);
  }

  @Override
  public boolean remove(PostId id) {
    return posts.remove(id) != null;
  }

  @Override
  public Stream<IndexedPost> all() {
    return List.copyOf(posts.values()).stream();
  }

  @Override
  public Set<PostId> ids() {
    return Set.copyOf(posts.keySet());
  }
}
