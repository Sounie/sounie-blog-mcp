package nz.sounie.blogmcp.search.application.embedding.spi;

import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;

/** Provider-pinned model, tokenizer and indexing-profile metadata. */
public record EmbeddingProviderProfile(
    String modelRevision,
    String tokenizerRevision,
    String indexProfileKey,
    int dimension,
    int contextTokens,
    PassageTokenBudget passageTokenBudget,
    EmbeddingTokenizer tokenizer) {}
