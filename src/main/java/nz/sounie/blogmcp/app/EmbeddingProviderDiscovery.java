package nz.sounie.blogmcp.app;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;

/** Discovers the provider supplied by the selected runtime artifact. */
public final class EmbeddingProviderDiscovery {

  private EmbeddingProviderDiscovery() {}

  public static EmbeddingProvider discover(ClassLoader classLoader) {
    return requireExactlyOne(
        serviceLoader(classLoader).stream().map(ServiceLoader.Provider::get).toList());
  }

  static EmbeddingProvider requireExactlyOne(Iterable<EmbeddingProvider> providers) {
    List<EmbeddingProvider> list = new ArrayList<>();
    providers.forEach(list::add);
    if (list.size() != 1) {
      throw new IllegalStateException(
          "Expected exactly one embedding provider on the runtime classpath, but found "
              + list.size());
    }
    return list.getFirst();
  }

  static ServiceLoader<EmbeddingProvider> serviceLoader(ClassLoader classLoader) {
    return ServiceLoader.load(EmbeddingProvider.class, classLoader);
  }
}
