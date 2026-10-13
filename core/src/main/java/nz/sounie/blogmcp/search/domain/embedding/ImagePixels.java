package nz.sounie.blogmcp.search.domain.embedding;

/**
 * A decoded image as an immutable 8-bit RGB raster: {@code width × height × 3} bytes, row-major
 * from the top-left pixel, channels interleaved R, G, B (search.md 3.6).
 */
public record ImagePixels(int width, int height, byte[] rgb) {

  public ImagePixels {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
