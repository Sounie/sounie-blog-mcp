package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2;

import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingTokenizer;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;

/**
 * The EmbeddingGemma 2 profile both Gemma2 providers share (ADR 0009 decision 2, ADR 0010): the
 * pinned tokenizer revision, dimension, context and provider-specific passage budget.
 */
public final class Gemma2Profile {

  public static final String TOKENIZER_REVISION = "914f7f89142e33e77833254d9c9b90c3cef7303b";

  /** The version of {@link #preparePassage}; change it whenever the template changes. */
  public static final String PASSAGE_PREPARATION_VERSION = "gemma2-document-template-v1";

  public static final PassageTokenBudget BUDGET =
      new PassageTokenBudget(3000, 256, 128, 4096, 8192);
  private static final int DIMENSION = 768;
  private static final int CONTEXT_TOKENS = 8192;

  private Gemma2Profile() {}

  /** The profile for one way of running the model, identified by its revision and index key. */
  public static EmbeddingProviderProfile of(
      String modelRevision, String indexProfileKey, Gemma2TokenCounter tokenizer) {
    return new EmbeddingProviderProfile(
        modelRevision,
        TOKENIZER_REVISION,
        indexProfileKey,
        DIMENSION,
        CONTEXT_TOKENS,
        BUDGET,
        tokenizer);
  }

  /**
   * Applies the document template and checks the result against the prepared-passage budget.
   *
   * @throws IllegalArgumentException if the prepared passage exceeds the budget
   */
  public static String preparePassage(String title, Passage passage, EmbeddingTokenizer tokens) {
    String prepared = Gemma2InputPreparation.gemma2Passage(title, passage.text());
    int count = tokens.countTokens(prepared);
    if (count > BUDGET.preparedPassageTokens()) {
      throw new IllegalArgumentException(
          "Prepared Gemma2 passage has "
              + count
              + " tokens, exceeding the "
              + BUDGET.preparedPassageTokens()
              + "-token budget");
    }
    return prepared;
  }
}
