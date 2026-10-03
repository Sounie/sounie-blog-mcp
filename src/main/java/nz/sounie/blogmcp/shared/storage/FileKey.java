package nz.sounie.blogmcp.shared.storage;

/**
 * The file-name form of an identifier: every character outside {@code [A-Za-z0-9_-]} is
 * percent-encoded (UTF-8 bytes, upper-case hex), so names are safe on every file system and cannot
 * contain path separators or {@code ..}. Encoding is a total, injective function.
 *
 * @param value the encoded form; only {@code [A-Za-z0-9_%-]}, never empty
 */
public record FileKey(String value) {

  /**
   * @throws IllegalArgumentException if the value is empty or has a character outside {@code
   *     [A-Za-z0-9_%-]}
   */
  public FileKey {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** Encodes an identifier. */
  public static FileKey of(String identifier) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
