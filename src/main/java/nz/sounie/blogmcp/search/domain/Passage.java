package nz.sounie.blogmcp.search.domain;

import java.util.Objects;

/** The exact text sent to the model for one chunk. */
public record Passage(String text) {

  public Passage {
    Objects.requireNonNull(text, "text");
  }
}
