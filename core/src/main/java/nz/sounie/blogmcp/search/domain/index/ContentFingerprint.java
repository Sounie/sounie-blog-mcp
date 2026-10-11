package nz.sounie.blogmcp.search.domain.index;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import nz.sounie.blogmcp.search.domain.text.WordSequence;

/**
 * SHA-256 (lower-case hex) over the index recipe, the normalised title and the normalised body.
 * Metadata is not included.
 */
public record ContentFingerprint(String value) {

  public ContentFingerprint {
    Objects.requireNonNull(value, "value");
  }

  /**
   * The parts are separated by newlines, which normalised text never contains, so moving a word
   * between title and body changes the fingerprint.
   */
  public static ContentFingerprint of(IndexRecipe recipe, String title, WordSequence body) {
    String content = String.join("\n", recipe.id(), WordSequence.of(title).text(), body.text());
    return new ContentFingerprint(
        HexFormat.of().formatHex(sha256().digest(content.getBytes(StandardCharsets.UTF_8))));
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      // Every Java platform must provide SHA-256 (MessageDigest specification).
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }
}
