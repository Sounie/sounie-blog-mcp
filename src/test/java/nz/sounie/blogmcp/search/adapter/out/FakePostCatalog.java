package nz.sounie.blogmcp.search.adapter.out;

import java.util.ArrayList;
import java.util.List;
import nz.sounie.blogmcp.search.application.PostCatalog;
import nz.sounie.blogmcp.search.domain.PostToIndex;

/** Fake of our {@link PostCatalog} port: the catalog's current posts, set by the test. */
public final class FakePostCatalog implements PostCatalog {

  private volatile List<PostToIndex> posts = List.of();

  public FakePostCatalog holding(PostToIndex... current) {
    this.posts = List.of(current);
    return this;
  }

  public FakePostCatalog holding(List<PostToIndex> current) {
    this.posts = new ArrayList<>(current);
    return this;
  }

  @Override
  public List<PostToIndex> currentPosts() {
    return List.copyOf(posts);
  }
}
