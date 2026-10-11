package nz.sounie.blogmcp.search.domain.text;

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

  /** The composition part of the index recipe, e.g. {@code tt64-p1}. */
  public String recipePart() {
    return "tt%d-p%d".formatted(titleTokenBudget, version);
  }

  /** One passage per chunk, in chunk order. */
  public List<Passage> compose(String title, List<Chunk> chunks, TokenCounter tokens) {
    return compose(title, chunks, tokens, PassageFormatter.standard());
  }

  /** One prepared passage per chunk, using the selected provider's formatting strategy. */
  public List<Passage> compose(
      String title, List<Chunk> chunks, TokenCounter tokens, PassageFormatter formatter) {
    String titleLine = titleLine(title, tokens);
    return chunks.stream().map(chunk -> formatter.format(titleLine, chunk.text())).toList();
  }

  private String titleLine(String title, TokenCounter tokens) {
    List<String> words = WordSequence.of(title).words();
    int fitting = WordCosts.of(words, tokens).fittingFrom(0, words.size(), titleTokenBudget);
    return String.join(" ", words.subList(0, fitting));
  }
}
