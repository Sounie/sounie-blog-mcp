package nz.sounie.blogmcp.app.mcp;

import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.catalog.adapter.out.InMemoryPostRepository;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.PostId;
import nz.sounie.blogmcp.catalog.domain.post.PostRepository;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;

/**
 * Fake of our {@link PostRepository} port: an in-memory repository whose lookups can be made to
 * fail with an unexpected exception, then repaired.
 */
final class BreakablePostRepository implements PostRepository {

  private final InMemoryPostRepository stored = new InMemoryPostRepository();
  private volatile RuntimeException failure;

  void breakWith(RuntimeException failure) {
    this.failure = failure;
  }

  void repair() {
    this.failure = null;
  }

  private void failIfBroken() {
    RuntimeException current = failure;
    if (current != null) {
      throw current;
    }
  }

  @Override
  public Optional<Post> findById(PostId id) {
    failIfBroken();
    return stored.findById(id);
  }

  @Override
  public Optional<Post> findByCanonicalUrl(CanonicalUrl url) {
    failIfBroken();
    return stored.findByCanonicalUrl(url);
  }

  @Override
  public Set<PostId> findIdsBySite(SiteId siteId) {
    return stored.findIdsBySite(siteId);
  }

  @Override
  public Set<SiteId> findSiteIds() {
    return stored.findSiteIds();
  }

  @Override
  public void save(Post post) {
    stored.save(post);
  }

  @Override
  public void delete(PostId id) {
    stored.delete(id);
  }
}
