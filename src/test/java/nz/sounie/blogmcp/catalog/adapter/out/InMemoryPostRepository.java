package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.catalog.domain.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.Post;
import nz.sounie.blogmcp.catalog.domain.PostId;
import nz.sounie.blogmcp.catalog.domain.PostRepository;
import nz.sounie.blogmcp.catalog.domain.SiteId;

/**
 * In-memory fake of {@link PostRepository}. Stores and returns copies, so unsaved changes to a
 * loaded post are not visible to later reads.
 */
public final class InMemoryPostRepository implements PostRepository {

  private final Map<PostId, Post> posts = new LinkedHashMap<>();

  @Override
  public synchronized Optional<Post> findById(PostId id) {
    return Optional.ofNullable(posts.get(id)).map(InMemoryPostRepository::copy);
  }

  @Override
  public synchronized Optional<Post> findByCanonicalUrl(CanonicalUrl url) {
    String wanted = url.normalisedForm();
    return posts.values().stream()
        .filter(post -> post.url().normalisedForm().equals(wanted))
        .findFirst()
        .map(InMemoryPostRepository::copy);
  }

  @Override
  public synchronized Set<PostId> findIdsBySite(SiteId siteId) {
    return posts.keySet().stream()
        .filter(id -> id.siteId().equals(siteId))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public synchronized Set<SiteId> findSiteIds() {
    return posts.keySet().stream()
        .map(PostId::siteId)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  @Override
  public synchronized void save(Post post) {
    posts.put(post.id(), copy(post));
  }

  @Override
  public synchronized void delete(PostId id) {
    posts.remove(id);
  }

  /** Whether a post with this external ID ({@code site:sourceId}) is stored. */
  public synchronized boolean isStored(String externalPostId) {
    return posts.keySet().stream()
        .anyMatch(
            id -> (id.siteId().value() + ":" + id.sourcePostId().value()).equals(externalPostId));
  }

  public synchronized int size() {
    return posts.size();
  }

  private static Post copy(Post post) {
    return Post.restore(
        post.id(),
        post.url(),
        post.title(),
        post.body(),
        post.completeness(),
        new LinkedHashSet<>(post.tags()),
        post.publishedAt(),
        post.updatedAt());
  }
}
