package nz.sounie.blogmcp.app;

import java.nio.file.Path;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;

/** Explicit local model revision, matching tokenizer assets and passage-token budget. */
public record Gemma2EmbeddingProfile(
    String modelRevision,
    String tokenizerRevision,
    Path tokenizerAssets,
    PassageTokenBudget passageTokenBudget) {

  public void validate() {
    throw new UnsupportedOperationException("Gemma2 profile validation is not implemented");
  }
}
