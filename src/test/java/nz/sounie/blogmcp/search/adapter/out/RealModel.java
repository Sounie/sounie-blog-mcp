package nz.sounie.blogmcp.search.adapter.out;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * One instance per test JVM of the real model adapters and of the real tokenizer (for checking
 * whole passages), so the model loads once. Not a test double: these are the real libraries.
 */
final class RealModel {

  private RealModel() {}

  private static final class Embedder {
    static final OnnxEmbedder INSTANCE = new OnnxEmbedder();
  }

  private static final class Counter {
    static final BgeTokenCounter INSTANCE = new BgeTokenCounter();
  }

  private static final class Tokenizer {
    static final HuggingFaceTokenizer INSTANCE = load();

    private static HuggingFaceTokenizer load() {
      try (InputStream in =
          RealModel.class.getResourceAsStream(BgeTokenCounter.TOKENIZER_RESOURCE)) {
        return HuggingFaceTokenizer.newInstance(in, Map.of("truncation", "false"));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  static OnnxEmbedder embedder() {
    return Embedder.INSTANCE;
  }

  static BgeTokenCounter tokenCounter() {
    return Counter.INSTANCE;
  }

  /** Content tokens of the whole text: no [CLS]/[SEP], no truncation. */
  static int contentTokens(String text) {
    return Tokenizer.INSTANCE.encode(text, false, false).getIds().length;
  }
}
