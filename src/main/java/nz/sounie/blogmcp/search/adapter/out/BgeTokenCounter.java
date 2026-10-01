package nz.sounie.blogmcp.search.adapter.out;

import nz.sounie.blogmcp.search.domain.TokenCounter;

/**
 * Counts tokens with the model's own tokenizer ({@code bge-small-en-v1.5-q-tokenizer.json}, bundled
 * in the LangChain4j jar) through DJL's {@code HuggingFaceTokenizer}, without special tokens and
 * without truncation.
 */
public final class BgeTokenCounter implements TokenCounter {

  /** Classpath location of the tokenizer file. */
  public static final String TOKENIZER_RESOURCE = "/bge-small-en-v1.5-q-tokenizer.json";

  @Override
  public int tokensIn(String word) {
    throw new UnsupportedOperationException("not implemented");
  }
}
