package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.jar.JarFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("jar")
class Gemma2OnnxDistributionJarTest {

  @Test
  @DisplayName("AC-SRCH-48, 51: the jar carries the pinned model, tokenizer, licence and notices")
  void jar_contains_pinned_model_tokenizer_licence_and_notices() throws IOException {
    String path = System.getProperty("blogmcp.distributionJar");
    assertThat(path).as("system property blogmcp.distributionJar, set by jarTest").isNotBlank();
    try (JarFile jar = new JarFile(path)) {
      assertThat(sha256(jar, "embeddinggemma-2-onnx/model_quantized.onnx"))
          .isEqualTo("d06edd601f851c633a2519304cbeb8dc6170d7ceb61b436625c17fb9b6e74953");
      assertThat(sha256(jar, "embeddinggemma-2-onnx/model_quantized.onnx_data"))
          .isEqualTo("278a7ff1248c3618e4bd11a607fc54f7bdc7778854230f3956d3f86bd9db4f3b");
      assertThat(sha256(jar, "embeddinggemma-2/tokenizer.json"))
          .isEqualTo("4d777ef5bdc1aa36227abdfb77c3e49e7b9c892d16e1b6bda41c393504828be4");
      assertThat(text(jar, "META-INF/licenses/LICENSE-EmbeddingGemma-2.0.txt"))
          .contains("Apache License")
          .contains("Version 2.0");
      assertThat(text(jar, "META-INF/NOTICE-EmbeddingGemma-2-ONNX.txt"))
          .contains("onnx-community/embeddinggemma-2-ONNX")
          .contains("daa72c51243991dfcaf9f9137d2c573d8f7790c0")
          .containsIgnoringCase("modified");
    }
  }

  private static String text(JarFile jar, String name) throws IOException {
    assertThat(jar.getEntry(name)).as("jar entry %s", name).isNotNull();
    try (InputStream input = jar.getInputStream(jar.getEntry(name))) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String sha256(JarFile jar, String name) throws IOException {
    assertThat(jar.getEntry(name)).as("jar entry %s", name).isNotNull();
    try (DigestInputStream input =
        new DigestInputStream(
            jar.getInputStream(jar.getEntry(name)), MessageDigest.getInstance("SHA-256"))) {
      input.transferTo(OutputStream.nullOutputStream());
      return HexFormat.of().formatHex(input.getMessageDigest().digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
