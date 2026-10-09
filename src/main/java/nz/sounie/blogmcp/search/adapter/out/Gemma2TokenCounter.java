package nz.sounie.blogmcp.search.adapter.out;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingTokenizer;
import nz.sounie.blogmcp.search.domain.text.TokenCounter;

/** Token accounting against the tokenizer shipped with the pinned EmbeddingGemma revision. */
final class Gemma2TokenCounter implements TokenCounter, EmbeddingTokenizer {

  static final String TOKENIZER_RESOURCE = "/embeddinggemma-2/tokenizer.json";
  private static final Map<String, String> OPTIONS = Map.of("truncation", "false");
  private final HuggingFaceTokenizer tokenizer;

  Gemma2TokenCounter() {
    try (InputStream json = Gemma2TokenCounter.class.getResourceAsStream(TOKENIZER_RESOURCE)) {
      if (json == null) {
        throw new IllegalStateException("Missing pinned Gemma2 tokenizer " + TOKENIZER_RESOURCE);
      }
      tokenizer = HuggingFaceTokenizer.newInstance(json, OPTIONS);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot load " + TOKENIZER_RESOURCE, e);
    }
  }

  @Override
  public int tokensIn(String word) {
    return countTokens(word);
  }

  @Override
  public int countTokens(String preparedInput) {
    return tokenizer.encode(preparedInput, false, false).getIds().length;
  }
}
