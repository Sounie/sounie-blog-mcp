package nz.sounie.blogmcp.search.application;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import nz.sounie.blogmcp.search.domain.image.ImageFetch;
import nz.sounie.blogmcp.search.domain.image.QueryImageSource;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.query.ImageSearchQuery;
import nz.sounie.blogmcp.search.domain.query.SearchScope;

/**
 * Answers an image search query (search.md 3.8): load the query image, embed it with the provider's
 * image embedder, and rank the posts in scope exactly as a text search does. Does not take the
 * index write lock and never calls the query embedder.
 */
public final class FindPostsLikeImage {

  private final VectorIndex index;
  private final ImageEmbedder imageEmbedder;
  private final QueryImageSource queryImages;

  public FindPostsLikeImage(
      VectorIndex index, ImageEmbedder imageEmbedder, QueryImageSource queryImages) {
    this.index = Objects.requireNonNull(index, "index");
    this.imageEmbedder = Objects.requireNonNull(imageEmbedder, "imageEmbedder");
    this.queryImages = Objects.requireNonNull(queryImages, "queryImages");
  }

  /**
   * @throws nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable if the image embedder
   *     fails, as from {@link SearchPosts}
   */
  public ImageSearch find(ImageSearchQuery query) {
    return switch (query.image().loadFrom(queryImages)) {
      case ImageFetch.Fetched fetched -> ranked(query.scope(), fetched.pixels());
      case ImageFetch.Rejected rejected -> new ImageSearch.ImageRefused(rejected.reason());
      case ImageFetch.Unavailable unavailable ->
          new ImageSearch.ImageUnavailable(unavailable.detail());
    };
  }

  private ImageSearch ranked(SearchScope scope, ImagePixels pixels) {
    return new ImageSearch.Ranked(scope.rank(index.all(), imageEmbedder.embedImage(pixels)));
  }
}
