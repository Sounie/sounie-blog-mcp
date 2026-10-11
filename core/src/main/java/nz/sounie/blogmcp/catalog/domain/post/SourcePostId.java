package nz.sounie.blogmcp.catalog.domain.post;

import java.util.Objects;

/** The platform's own identifier for a post. Not blank. */
public record SourcePostId(String value) {

  public SourcePostId {
    Objects.requireNonNull(value, "source post ID");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Source post ID must not be blank");
    }
  }
}
