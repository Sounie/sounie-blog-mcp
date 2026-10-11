package nz.sounie.blogmcp.search.application.embedding.spi;

/** Counts a complete, prepared provider input with the provider's pinned tokenizer. */
@FunctionalInterface
public interface EmbeddingTokenizer {

  int countTokens(String preparedInput);
}
