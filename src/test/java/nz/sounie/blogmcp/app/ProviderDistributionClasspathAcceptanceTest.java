package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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

  @Test
  @DisplayName("AC-SRCH-40, 43; AC-APP-12: distribution has one separate selected provider JAR")
  void distribution_contains_only_the_selected_provider_artifact() throws IOException {
    Path libraries = Path.of(System.getProperty("blogmcp.distributionLibraries"));
    String selected = System.getProperty("blogmcp.embeddingProvider");

    List<String> jars;
    try (var files = Files.list(libraries)) {
      jars =
          files
              .filter(path -> path.toString().endsWith(".jar"))
              .map(path -> path.getFileName().toString())
              .toList();
    }

    assertThat(selected).isIn("onnx", "gemma2");
    assertThat(jars)
        .anyMatch(name -> name.contains("blog-mcp"))
        .anyMatch(name -> name.contains("embedding-provider-" + selected));
    assertThat(jars.stream().filter(name -> name.contains("embedding-provider-"))).hasSize(1);
    assertThat(jars.stream().filter(name -> name.contains("embedding-provider-")))
        .noneMatch(name -> name.contains("embedding-provider-" + otherProvider(selected)));
  }

  @Test
  @DisplayName("AC-SRCH-42: selected distribution discovers exactly its one provider service")
  void discovers_provider_from_selected_distribution_classpath() {
    EmbeddingProvider provider =
        EmbeddingProviderDiscovery.discover(Thread.currentThread().getContextClassLoader());

    assertThat(provider.profile().indexProfileKey())
        .isEqualTo(
            System.getProperty("blogmcp.embeddingProvider").equals("onnx")
                ? "onnx-bge-small-en-v1.5-q-v1"
                : "gemma2-914f7f89-v1");
  }

  @Test
  @DisplayName("AC-SRCH-48: the Gemma2 artifact preserves its pinned tokenizer and Apache license")
  void gemma2_artifact_contains_tokenizer_and_license_notice() throws IOException {
    Assumptions.assumeTrue("gemma2".equals(System.getProperty("blogmcp.embeddingProvider")));
    Path libraries = Path.of(System.getProperty("blogmcp.distributionLibraries"));
    Path gemmaJar;
    try (var files = Files.list(libraries)) {
      gemmaJar =
          files
              .filter(path -> path.getFileName().toString().contains("embedding-provider-gemma2"))
              .findFirst()
              .orElseThrow();
    }

    try (JarFile jar = new JarFile(gemmaJar.toFile())) {
      List<String> entries = jar.stream().map(entry -> entry.getName()).toList();
      assertThat(entries).anyMatch(name -> name.endsWith("tokenizer.json"));
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

  private static String otherProvider(String selected) {
    return selected.equals("onnx") ? "gemma2" : "onnx";
  }
}
