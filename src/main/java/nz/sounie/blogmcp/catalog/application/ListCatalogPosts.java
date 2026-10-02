package nz.sounie.blogmcp.catalog.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import nz.sounie.blogmcp.catalog.domain.Post;
import nz.sounie.blogmcp.catalog.domain.PostRepository;
import nz.sounie.blogmcp.catalog.domain.Tag;
import nz.sounie.blogmcp.shared.query.CatalogPostState;
import nz.sounie.blogmcp.shared.query.CatalogPosts;

/** Serves the catalog's current posts through the shared query contract (AC-SRCH-31). */
public final class ListCatalogPosts implements CatalogPosts {

  private final PostRepository posts;

  public ListCatalogPosts(PostRepository posts) {
    this.posts = Objects.requireNonNull(posts, "posts");
  }

  /** Every stored post, summary-only ones included, sorted by post ID as a plain string. */
  @Override
  public List<CatalogPostState> currentPosts() {
    return posts.findSiteIds().stream()
        .flatMap(site -> posts.findIdsBySite(site).stream())
        .flatMap(id -> posts.findById(id).stream())
        .map(ListCatalogPosts::stateOf)
        .sorted(Comparator.comparing(CatalogPostState::postId))
        .toList();
  }

  private static CatalogPostState stateOf(Post post) {
    return new CatalogPostState(
        post.id().external(),
        post.id().siteId().value(),
        post.url().value().toString(),
        post.title().value(),
        post.body().text(),
        post.completeness().name(),
        IntegrationEvents.names(post.tags(), Tag::value),
        post.publishedAt(),
        post.updatedAt());
  }
}
