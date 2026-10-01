package nz.sounie.blogmcp.catalog.domain;

import java.util.Objects;

/** Post content as plain text. May be empty, never null. */
public record Body(String text) {

  public Body {
    Objects.requireNonNull(text, "body");
  }
}
