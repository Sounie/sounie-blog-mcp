package nz.sounie.blogmcp.search.domain.text;

import java.util.Objects;

/** The exact text sent to the model for one chunk. */
public record Passage(String text) {

  public Passage {
    Objects.requireNonNull(text, "text");
  }
}
