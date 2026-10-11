package nz.sounie.blogmcp.catalog.domain.post;

import java.util.Objects;

/** Plain-text title. May be empty, never null. */
public record Title(String value) {

  public Title {
    Objects.requireNonNull(value, "title");
  }
}
