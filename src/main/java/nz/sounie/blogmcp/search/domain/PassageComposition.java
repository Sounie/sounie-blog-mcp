package nz.sounie.blogmcp.search.domain;

import java.util.List;

/**
 * Builds passages: the title line, a newline, then the chunk text. The title line is the normalised
 * title cut to its longest whole-word prefix of at most {@link #TITLE_TOKEN_BUDGET} tokens. A blank
 * title is omitted.
 */
public record PassageComposition(int titleTokenBudget, int version) {

  public static final int TITLE_TOKEN_BUDGET = 64;
  public static final int VERSION = 1;

  public static PassageComposition standard() {
    return new PassageComposition(TITLE_TOKEN_BUDGET, VERSION);
  }

  /** One passage per chunk, in chunk order. */
  public List<Passage> compose(String title, List<Chunk> chunks, TokenCounter tokens) {
    throw new UnsupportedOperationException("not implemented");
  }
}
