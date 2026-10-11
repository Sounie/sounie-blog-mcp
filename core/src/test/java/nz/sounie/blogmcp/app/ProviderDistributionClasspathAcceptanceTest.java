package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Run by each provider module's {@code jarTest} against that module's distribution jar (ADR 0010).
 * The module states what its jar must contain through system properties.
 */
@Tag("jar")
class ProviderDistributionClasspathAcceptanceTest {

  private static final String SERVICE = "META-INF/services/" + EmbeddingProvider.class.getName();
  private static final String PROVIDER_CODE = "nz/sounie/blogmcp/search/adapter/out/embedding/";

  @Test
  @DisplayName("AC-SRCH-40; AC-APP-12: the distribution is one fat jar with exactly one provider")
  void distribution_contains_exactly_its_own_provider() throws IOException {
    try (JarFile jar = new JarFile(distributionJar().toFile())) {
      assertThat(jar.getEntry("nz/sounie/blogmcp/app/Main.class")).isNotNull();
      assertThat(jar.stream().filter(entry -> entry.getName().equals(SERVICE)).count())
          .isEqualTo(1L);
      try (InputStream service = jar.getInputStream(jar.getJarEntry(SERVICE))) {
        assertThat(new String(service.readAllBytes(), StandardCharsets.UTF_8).lines().toList())
            .containsExactly(required("blogmcp.expectedProvider"));
      }
      List<String> providerPackages =
          Arrays.stream(required("blogmcp.providerPackages").split(","))
              .map(name -> name.replace('.', '/'))
              .toList();
      assertThat(
              jar.stream()
                  .map(JarEntry::getName)
                  .filter(name -> name.startsWith(PROVIDER_CODE) && name.endsWith(".class"))
                  .map(name -> name.substring(0, name.lastIndexOf('/')))
                  .distinct())
          .isNotEmpty()
          .allSatisfy(packageDir -> assertThat(providerPackages).contains(packageDir));
    }
  }

  @Test
  @DisplayName("AC-SRCH-42: the distribution discovers exactly its one provider service")
  void discovers_provider_from_distribution_classpath() throws IOException {
    try (URLClassLoader distributionLoader = distributionClassLoader()) {
      EmbeddingProvider provider = EmbeddingProviderDiscovery.discover(distributionLoader);
      assertThat(provider.profile().indexProfileKey())
          .isEqualTo(required("blogmcp.expectedProfileKey"));
    }
  }

  private static String required(String property) {
    String value = System.getProperty(property);
    assertThat(value)
        .as("system property %s, set by the provider's jarTest", property)
        .isNotBlank();
    return value;
  }

  private static Path distributionJar() {
    return Path.of(required("blogmcp.distributionJar"));
  }

  /** Loads classes parent-first, but reads the provider service only from the distribution jar. */
  static URLClassLoader distributionClassLoader() throws IOException {
    return new URLClassLoader(
        new URL[] {distributionJar().toUri().toURL()},
        Thread.currentThread().getContextClassLoader()) {
      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        return SERVICE.equals(name) ? findResources(name) : super.getResources(name);
      }
    };
  }
}
