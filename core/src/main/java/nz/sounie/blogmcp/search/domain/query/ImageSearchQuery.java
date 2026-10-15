package nz.sounie.blogmcp.search.domain.query;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.image.ImageQuery;

/** An image query with a search scope (search.md 3.8). */
public record ImageSearchQuery(ImageQuery image, SearchScope scope) {

  public ImageSearchQuery {
    Objects.requireNonNull(image, "image");
    Objects.requireNonNull(scope, "scope");
  }
}
