package nz.sounie.blogmcp.catalog.adapter.out;

/** The {@code format} version of the catalog's stored files. */
final class StoredFormat {

  /** The only format this version reads and writes. */
  static final int CURRENT = 1;

  private StoredFormat() {}

  /**
   * @throws IllegalArgumentException if the stored format is missing or not {@link #CURRENT}
   */
  static void require(Integer stored) {
    if (!Integer.valueOf(CURRENT).equals(stored)) {
      throw new IllegalArgumentException(
          "Unknown format " + stored + "; this version reads format " + CURRENT);
    }
  }
}
