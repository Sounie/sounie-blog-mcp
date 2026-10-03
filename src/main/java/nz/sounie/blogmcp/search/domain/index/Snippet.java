package nz.sounie.blogmcp.search.domain.index;

import java.util.Objects;

/**
 * The start of a post's best chunk, shown with a search result: the first {@link #MAX_WORDS} words,
 * followed by {@code " …"} when the chunk is longer ({@link #truncated()}).
 */
public record Snippet(String text, boolean truncated) {

  public static final int MAX_WORDS = 60;

  public Snippet {
    Objects.requireNonNull(text, "text");
  }

  /** The snippet of a chunk's (already normalised, single-space separated) text. */
  public static Snippet of(String chunkText) {
    throw new UnsupportedOperationException("not implemented yet (AC-SRCH-39)");
  }
}
