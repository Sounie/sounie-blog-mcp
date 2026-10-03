package nz.sounie.blogmcp.shared.storage;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The file-name form of an identifier: every character outside {@code [a-z0-9_-]} (including
 * upper-case letters) is percent-encoded as its UTF-8 bytes in lower-case hex, so every key is
 * fully lower case. Names are therefore safe on every file system, including case-insensitive ones
 * (macOS APFS), and cannot contain path separators or {@code ..}. Encoding is a total, injective
 * function.
 *
 * @param value the encoded form; only {@code [a-z0-9_%-]}, never empty
 */
public record FileKey(String value) {

  private static final Pattern ENCODED = Pattern.compile("[a-z0-9_%-]+");

  /** The characters kept as they are; each is a single UTF-8 byte. */
  private static final String UNCHANGED = "abcdefghijklmnopqrstuvwxyz0123456789_-";

  private static final HexFormat LOWER_CASE_HEX = HexFormat.of();

  /**
   * @throws IllegalArgumentException if the value is empty or has a character outside {@code
   *     [a-z0-9_%-]}
   */
  public FileKey {
    Objects.requireNonNull(value, "file key");
    if (!ENCODED.matcher(value).matches()) {
      throw new IllegalArgumentException("Not a file key ([a-z0-9_%-]+): '" + value + "'");
    }
  }

  /** Encodes an identifier. */
  public static FileKey of(String identifier) {
    StringBuilder key = new StringBuilder();
    for (byte b : identifier.getBytes(StandardCharsets.UTF_8)) {
      key.append(encoded(b));
    }
    return new FileKey(key.toString());
  }

  /**
   * The byte itself if it is a kept character, otherwise {@code %} and two lower-case hex digits.
   */
  private static String encoded(byte b) {
    return UNCHANGED.indexOf(b) >= 0
        ? String.valueOf((char) b)
        : "%" + LOWER_CASE_HEX.toHexDigits(b);
  }
}
