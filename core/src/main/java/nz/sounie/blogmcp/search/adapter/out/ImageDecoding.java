package nz.sounie.blogmcp.search.adapter.out;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import nz.sounie.blogmcp.search.domain.image.ImageFetch;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;

/**
 * Decodes JPEG, PNG and GIF bytes to image pixels with ImageIO (search.md 3.7). The declared
 * dimensions are checked before any pixel is decoded, so an oversized image is refused cheaply.
 * Like PIL's {@code convert("RGB")}, it decodes the first frame, drops alpha without compositing,
 * expands palettes and replicates greyscale.
 */
final class ImageDecoding {

  /** A decoded side under this many pixels is a tracking pixel or an icon, not content. */
  static final int MIN_SIDE = 64;

  private static final ImageFetch UNDECODABLE = new ImageFetch.Rejected(ImageRejection.UNDECODABLE);

  /** A condition on the declared width and height. */
  @FunctionalInterface
  private interface SizeCondition {
    boolean holds(long width, long height);
  }

  /** One check on the declared size; the first that is violated decides. */
  private record SizeRule(SizeCondition violated, ImageRejection reason) {}

  private static final List<SizeRule> SIZE_RULES =
      List.of(
          new SizeRule(
              (width, height) -> width * height > ImagePixels.MAX_PIXELS, ImageRejection.TOO_LARGE),
          new SizeRule(
              (width, height) -> Math.min(width, height) < MIN_SIDE, ImageRejection.TOO_SMALL));

  private ImageDecoding() {}

  /** Never throws: anything ImageIO cannot read is {@code UNDECODABLE}. */
  static ImageFetch decode(byte[] bytes) {
    try (ImageInputStream input =
        new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      return readers.hasNext() ? decodeWith(readers.next(), input) : UNDECODABLE;
    } catch (IOException | RuntimeException e) {
      // justified: ImageIO's readers throw unchecked exceptions for some corrupt files.
      return UNDECODABLE;
    }
  }

  private static ImageFetch decodeWith(ImageReader reader, ImageInputStream input)
      throws IOException {
    try {
      reader.setInput(input, true, true);
      Optional<ImageRejection> refused = sizeRefusal(reader.getWidth(0), reader.getHeight(0));
      return refused.isPresent()
          ? new ImageFetch.Rejected(refused.get())
          : new ImageFetch.Fetched(pixelsOf(reader.read(0)));
    } finally {
      reader.dispose();
    }
  }

  private static Optional<ImageRejection> sizeRefusal(long width, long height) {
    return SIZE_RULES.stream()
        .filter(rule -> rule.violated().holds(width, height))
        .map(SizeRule::reason)
        .findFirst();
  }

  private static ImagePixels pixelsOf(BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    int[] rgb = Conversion.of(image).rgb(image, width, height);
    byte[] samples = new byte[rgb.length * 3];
    for (int i = 0; i < rgb.length; i++) {
      samples[i * 3] = (byte) (rgb[i] >> 16);
      samples[i * 3 + 1] = (byte) (rgb[i] >> 8);
      samples[i * 3 + 2] = (byte) rgb[i];
    }
    return new ImagePixels(width, height, samples);
  }

  /** How a decoded image becomes packed RGB values ({@code 0xRRGGBB}), alpha ignored. */
  private enum Conversion {
    /**
     * Grey samples replicated to R, G and B, scaled to 8 bits. Java's colour conversion would apply
     * a gamma curve to them; PIL does not.
     */
    GREY {
      @Override
      int[] rgb(BufferedImage image, int width, int height) {
        int max = (1 << image.getColorModel().getComponentSize(0)) - 1;
        return Arrays.stream(image.getRaster().getSamples(0, 0, width, height, 0, (int[]) null))
            .map(sample -> (int) (((long) sample * 255 + max / 2) / max))
            .map(grey -> grey << 16 | grey << 8 | grey)
            .toArray();
      }
    },
    /** Colour and palette images: the colour model's non-premultiplied sRGB values. */
    COLOUR {
      @Override
      int[] rgb(BufferedImage image, int width, int height) {
        return image.getRGB(0, 0, width, height, null, 0, width);
      }
    };

    abstract int[] rgb(BufferedImage image, int width, int height);

    static Conversion of(BufferedImage image) {
      return image.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY
          ? GREY
          : COLOUR;
    }
  }
}
