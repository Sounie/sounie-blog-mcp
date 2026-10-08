package nz.sounie.blogmcp.app;

import java.util.Map;

/** Startup selection of the shared search embedding provider. */
public record EmbeddingProviderSelection(Provider provider) {

  public enum Provider {
    ONNX,
    GEMMA2
  }

  public static EmbeddingProviderSelection fromEnvironment(Map<String, String> environment) {
    throw new UnsupportedOperationException("Provider selection is not implemented");
  }
}
