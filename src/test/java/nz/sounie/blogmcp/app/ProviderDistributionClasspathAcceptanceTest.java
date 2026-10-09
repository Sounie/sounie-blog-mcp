package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("jar")
class ProviderDistributionClasspathAcceptanceTest {

  private static final String GEMMA2_TOKENIZER_SHA256 =
      "4d777ef5bdc1aa36227abdfb77c3e49e7b9c892d16e1b6bda41c393504828be4";

  @Test
  @DisplayName("AC-SRCH-40, 43; AC-APP-12: distribution is one fat jar for the selected provider")
  void distribution_is_one_fat_jar_for_the_selected_provider() throws IOException {
    String selected = selectedProvider();
    Path distribution = distributionJar();

    assertThat(distribution.getFileName().toString()).isEqualTo("blog-mcp-" + selected + ".jar");
    try (JarFile jar = new JarFile(distribution.toFile())) {
      assertThat(jar.getEntry("nz/sounie/blogmcp/app/Main.class")).isNotNull();
      String servicePath = "META-INF/services/" + EmbeddingProvider.class.getName();
      assertThat(jar.stream().filter(entry -> entry.getName().equals(servicePath)).count())
          .isEqualTo(1L);
      String selectedClass =
          "nz.sounie.blogmcp.search.adapter.out."
              + (selected.equals("onnx") ? "OnnxEmbedder" : "Gemma2Embedder");
      String excludedClass =
          "nz.sounie.blogmcp.search.adapter.out."
              + (selected.equals("onnx") ? "Gemma2Embedder" : "OnnxEmbedder");
      try (InputStream service = jar.getInputStream(jar.getJarEntry(servicePath))) {
        assertThat(
                new String(service.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .lines()
                    .toList())
            .containsExactly(selectedClass);
      }
      String selectedCounter = selected.equals("onnx") ? "BgeTokenCounter" : "Gemma2TokenCounter";
      String excludedCounter = selected.equals("onnx") ? "Gemma2TokenCounter" : "BgeTokenCounter";
      assertThat(jar.getEntry("nz/sounie/blogmcp/search/adapter/out/" + selectedCounter + ".class"))
          .isNotNull();
      assertThat(jar.getEntry("nz/sounie/blogmcp/search/adapter/out/" + excludedCounter + ".class"))
          .isNull();
      assertThat(
              jar.stream()
                  .map(java.util.jar.JarEntry::getName)
                  .filter(
                      name ->
                          name.startsWith("nz/sounie/blogmcp/search/adapter/out/")
                              && (name.contains("OnnxEmbedder") || name.contains("Gemma2Embedder")))
                  .toList())
          .anyMatch(
              name -> name.contains(selectedClass.substring(selectedClass.lastIndexOf('.') + 1)))
          .noneMatch(
              name -> name.contains(excludedClass.substring(excludedClass.lastIndexOf('.') + 1)));
    }
  }

  @Test
  @DisplayName("AC-SRCH-42: selected distribution discovers exactly its one provider service")
  void discovers_provider_from_selected_distribution_classpath() throws IOException {
    try (URLClassLoader distributionLoader = distributionClassLoader()) {
      EmbeddingProvider provider = EmbeddingProviderDiscovery.discover(distributionLoader);
      assertThat(provider.profile().indexProfileKey())
          .isEqualTo(
              selectedProvider().equals("onnx")
                  ? "onnx-bge-small-en-v1.5-q-v1"
                  : "gemma2-914f7f89-v1");
    }
  }

  @Test
  @DisplayName("AC-SRCH-48: the Gemma2 artifact preserves its pinned tokenizer and Apache license")
  void gemma2_artifact_contains_tokenizer_and_license_notice() throws IOException {
    Assumptions.assumeTrue("gemma2".equals(System.getProperty("blogmcp.embeddingProvider")));
    try (JarFile jar = new JarFile(distributionJar().toFile())) {
      List<String> tokenizerHashes =
          jar.stream()
              .filter(entry -> entry.getName().endsWith("tokenizer.json"))
              .map(
                  entry -> {
                    try {
                      return sha256(jar, entry.getName());
                    } catch (IOException e) {
                      throw new UncheckedIOException(e);
                    }
                  })
              .toList();
      assertThat(tokenizerHashes).contains(GEMMA2_TOKENIZER_SHA256);
      List<String> entries = jar.stream().map(java.util.jar.JarEntry::getName).toList();
      List<String> notices =
          entries.stream()
              .filter(
                  name ->
                      name.toLowerCase().contains("license")
                          || name.toLowerCase().contains("notice"))
              .toList();
      assertThat(notices).isNotEmpty();
      assertThat(notices)
          .anySatisfy(
              name -> {
                try (InputStream input = jar.getInputStream(jar.getEntry(name))) {
                  String text =
                      new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                  assertThat(text).containsIgnoringCase("apache").contains("2.0");
                } catch (IOException e) {
                  throw new AssertionError(e);
                }
              });
    }
  }

  @Test
  @DisplayName("AC-SRCH-41: provider distributions cannot be assembled without explicit selection")
  void the_distribution_test_is_tied_to_explicit_provider_build_selection() {
    assertThat(System.getProperty("blogmcp.embeddingProvider"))
        .as("set from the required -PembeddingProvider build property")
        .isIn("onnx", "gemma2");
  }

  @Test
  @DisplayName("AC-SRCH-41: absent, blank and unsupported build selections are rejected")
  void rejects_missing_blank_and_unsupported_build_selection() throws Exception {
    assertRejected(List.of());
    assertRejected(List.of("-PembeddingProvider="));
    assertRejected(List.of("-PembeddingProvider=unsupported"));
  }

  @Test
  @DisplayName("AC-SRCH-40: either supported build selection configures successfully")
  void accepts_each_supported_build_selection() throws Exception {
    assertAccepted("onnx");
    assertAccepted("gemma2");
  }

  private static void assertRejected(List<String> selection) throws Exception {
    List<String> command = new ArrayList<>(List.of("./gw"));
    command.addAll(selection);
    command.addAll(List.of("help", "--quiet"));
    GradleResult result = run(command);
    assertThat(result.exitCode()).isNotZero();
    assertThat(result.output()).containsIgnoringCase("embeddingProvider");
  }

  private static void assertAccepted(String provider) throws Exception {
    GradleResult result =
        run(List.of("./gw", "-PembeddingProvider=" + provider, "help", "--quiet"));
    assertThat(result.exitCode()).as(result.output()).isZero();
  }

  private static GradleResult run(List<String> command) throws Exception {
    Process process =
        new ProcessBuilder(command)
            .directory(Path.of(System.getProperty("user.dir")).toFile())
            .redirectErrorStream(true)
            .start();
    try (InputStream output = process.getInputStream()) {
      byte[] bytes = output.readAllBytes();
      assertThat(process.waitFor(Duration.ofMinutes(3).toMillis(), TimeUnit.MILLISECONDS))
          .as("Gradle command completes")
          .isTrue();
      return new GradleResult(
          process.exitValue(), new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    }
  }

  private record GradleResult(int exitCode, String output) {}

  private static String selectedProvider() {
    String selected = System.getProperty("blogmcp.embeddingProvider");
    assertThat(selected)
        .as("set from the required -PembeddingProvider build property")
        .isIn("onnx", "gemma2");
    return selected;
  }

  private static Path distributionJar() {
    String path = System.getProperty("blogmcp.distributionJar");
    assertThat(path).as("set to the selected-provider fat jar by jarTest").isNotBlank();
    return Path.of(path);
  }

  static URLClassLoader distributionClassLoader() throws IOException {
    String service = "META-INF/services/" + EmbeddingProvider.class.getName();
    return new URLClassLoader(
        new URL[] {distributionJar().toUri().toURL()},
        Thread.currentThread().getContextClassLoader()) {
      @Override
      public Enumeration<URL> getResources(String name) throws IOException {
        return service.equals(name) ? findResources(name) : super.getResources(name);
      }
    };
  }

  private static String sha256(JarFile jar, String entryName) throws IOException {
    try (InputStream input = jar.getInputStream(jar.getJarEntry(entryName))) {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(input.readAllBytes()));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
