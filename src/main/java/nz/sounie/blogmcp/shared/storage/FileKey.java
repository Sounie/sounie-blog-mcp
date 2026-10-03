package nz.sounie.blogmcp.shared.storage;

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

  /**
   * @throws IllegalArgumentException if the value is empty or has a character outside {@code
   *     [a-z0-9_%-]}
   */
  public FileKey {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** Encodes an identifier. */
  public static FileKey of(String identifier) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
