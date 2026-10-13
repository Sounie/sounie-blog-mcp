package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import java.io.Serial;

/**
 * The resize's intermediate image would exceed its 48 MiB bound (search.md 3.6). {@link
 * Gemma2OnnxEmbedder} reports it as {@code EmbedderUnavailable}.
 */
final class ImageTooLargeToResize extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  ImageTooLargeToResize(int width, int height) {
    super(
        "Resizing needs an intermediate image of "
            + width
            + " x "
            + height
            + ", over "
            + IntermediateSize.MAX_SAMPLES
            + " samples");
  }
}
