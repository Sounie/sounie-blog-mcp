package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import java.util.stream.LongStream;

/** The text-model input ids for one image: {@code [2, 255999, 258880 × n, 258882, 1]}. */
final class ImageInputIds {

  private static final long[] BEGIN = {2, 255999};
  private static final long IMAGE_SOFT_TOKEN = 258880;
  private static final long[] END = {258882, 1};

  private ImageInputIds() {}

  /** The ids for an image with this grid's image tokens. Images take no task prefix. */
  static long[] of(PatchGrid grid) {
    return LongStream.concat(
            LongStream.concat(
                LongStream.of(BEGIN),
                LongStream.generate(() -> IMAGE_SOFT_TOKEN).limit(grid.imageTokens())),
            LongStream.of(END))
        .toArray();
  }
}
