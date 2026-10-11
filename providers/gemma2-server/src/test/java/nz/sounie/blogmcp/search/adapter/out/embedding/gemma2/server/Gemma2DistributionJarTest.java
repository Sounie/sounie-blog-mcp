package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("jar")
class Gemma2DistributionJarTest {

  private static final String TOKENIZER_SHA256 =
      "4d777ef5bdc1aa36227abdfb77c3e49e7b9c892d16e1b6bda41c393504828be4";

  @Test
  @DisplayName("AC-SRCH-48: the Gemma2 jar carries its pinned tokenizer, licence and notice")
  void jar_contains_pinned_tokenizer_licence_and_notice() throws IOException {
    String path = System.getProperty("blogmcp.distributionJar");
    assertThat(path).as("system property blogmcp.distributionJar, set by jarTest").isNotBlank();
    try (JarFile jar = new JarFile(path)) {
      assertThat(sha256(read(jar, "embeddinggemma-2/tokenizer.json"))).isEqualTo(TOKENIZER_SHA256);
      assertThat(
              new String(
                  read(jar, "META-INF/licenses/LICENSE-EmbeddingGemma-2.0.txt"),
                  StandardCharsets.UTF_8))
          .contains("Apache License")
          .contains("Version 2.0");
      assertThat(
              new String(read(jar, "META-INF/NOTICE-EmbeddingGemma.txt"), StandardCharsets.UTF_8))
          .containsIgnoringCase("embeddinggemma");
    }
  }

  private static byte[] read(JarFile jar, String name) throws IOException {
    assertThat(jar.getEntry(name)).as("jar entry %s", name).isNotNull();
    try (InputStream input = jar.getInputStream(jar.getEntry(name))) {
      return input.readAllBytes();
    }
  }

  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
