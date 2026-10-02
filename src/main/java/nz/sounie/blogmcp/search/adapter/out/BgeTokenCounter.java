package nz.sounie.blogmcp.search.adapter.out;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import nz.sounie.blogmcp.search.domain.TokenCounter;

/**
 * Counts tokens with the model's own tokenizer ({@code bge-small-en-v1.5-q-tokenizer.json}, bundled
 * in the LangChain4j jar) through DJL's {@code HuggingFaceTokenizer}, without special tokens and
 * without truncation.
 */
public final class BgeTokenCounter implements TokenCounter {

  /** Classpath location of the tokenizer file. */
  public static final String TOKENIZER_RESOURCE = "/bge-small-en-v1.5-q-tokenizer.json";

  /**
   * DJL truncates to 512 tokens unless told otherwise, although the tokenizer file has no
   * truncation; counting must never truncate.
   */
  private static final Map<String, String> OPTIONS = Map.of("truncation", "false");

  private final HuggingFaceTokenizer tokenizer;

  /**
   * Loads the tokenizer from the classpath.
   *
   * @throws UncheckedIOException if the tokenizer file cannot be read
   */
  public BgeTokenCounter() {
    try (InputStream json = BgeTokenCounter.class.getResourceAsStream(TOKENIZER_RESOURCE)) {
      this.tokenizer = HuggingFaceTokenizer.newInstance(json, OPTIONS);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot load " + TOKENIZER_RESOURCE, e);
    }
  }

  @Override
  public int tokensIn(String word) {
    return tokenizer.encode(word, false, false).getIds().length;
  }
}
