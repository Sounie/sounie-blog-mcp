package nz.sounie.blogmcp.search.application.embedding.spi;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;

/** Provider-pinned model, tokenizer and indexing-profile metadata. */
public record EmbeddingProviderProfile(
    String modelRevision,
    String tokenizerRevision,
    String indexProfileKey,
    int dimension,
    int contextTokens,
    PassageTokenBudget passageTokenBudget,
    EmbeddingTokenizer tokenizer) {

  public EmbeddingProviderProfile {
    if (modelRevision == null || modelRevision.isBlank()) {
      throw new IllegalArgumentException("modelRevision must not be blank");
    }
    if (tokenizerRevision == null || tokenizerRevision.isBlank()) {
      throw new IllegalArgumentException("tokenizerRevision must not be blank");
    }
    if (indexProfileKey == null || indexProfileKey.isBlank()) {
      throw new IllegalArgumentException("indexProfileKey must not be blank");
    }
    if (dimension < 1 || contextTokens < 1) {
      throw new IllegalArgumentException("Provider dimension and context must be positive");
    }
    Objects.requireNonNull(passageTokenBudget, "passageTokenBudget").validate();
    Objects.requireNonNull(tokenizer, "tokenizer");
    if (passageTokenBudget.supportedContextTokens() > contextTokens) {
      throw new IllegalArgumentException("Passage budget exceeds provider context");
    }
  }
}
