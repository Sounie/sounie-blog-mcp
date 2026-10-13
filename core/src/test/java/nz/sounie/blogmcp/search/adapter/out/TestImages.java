package nz.sounie.blogmcp.search.adapter.out;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/** Real image files for the {@code HttpImageSource} tests, encoded with the JDK's ImageIO. */
final class TestImages {

  private TestImages() {}

  /** An opaque image of one colour. */
  static BufferedImage solid(int width, int height, int rgb) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    fill(image, rgb);
    return image;
  }

  /** A non-premultiplied ARGB image whose every pixel has this alpha and colour. */
  static BufferedImage translucent(int width, int height, int alpha, int rgb) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    fill(image, (alpha << 24) | (rgb & 0xFFFFFF));
    return image;
  }

  private static void fill(BufferedImage image, int argb) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        image.setRGB(x, y, argb);
      }
    }
  }

  static byte[] png(BufferedImage image) {
    return encode(image, "png");
  }

  static byte[] png(int width, int height) {
    return png(solid(width, height, 0x336699));
  }

  static byte[] jpeg(BufferedImage image) {
    return encode(image, "jpeg");
  }

  private static byte[] encode(BufferedImage image, String format) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      if (!ImageIO.write(image, format, out)) {
        throw new IllegalStateException("No ImageIO writer for " + format);
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** An animated GIF: one frame per image, in order. */
  static byte[] animatedGif(BufferedImage... frames) {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
      writer.setOutput(stream);
      writer.prepareWriteSequence(null);
      for (BufferedImage frame : frames) {
        writer.writeToSequence(new IIOImage(frame, null, null), null);
      }
      writer.endWriteSequence();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      writer.dispose();
    }
    return out.toByteArray();
  }

  /**
   * A PNG that only declares its size: signature, IHDR and IEND, with no pixel data. Reading its
   * dimensions works; decoding its pixels fails.
   */
  static byte[] pngDeclaring(int width, int height) {
    ByteBuffer header = ByteBuffer.allocate(13);
    header.putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0);
    header.put((byte) 0).put((byte) 0);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
    chunk(out, "IHDR", header.array());
    chunk(out, "IEND", new byte[0]);
    return out.toByteArray();
  }

  private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
    byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
    out.writeBytes(typeBytes);
    out.writeBytes(data);
    out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
  }

  /** The image padded with zero bytes after its end to exactly {@code length} bytes. */
  static byte[] paddedTo(byte[] image, int length) {
    return Arrays.copyOf(image, length);
  }

  /** The expected RGB samples of an image where every pixel has this colour. */
  static byte[] rgbOf(int width, int height, int rgb) {
    byte[] samples = new byte[width * height * 3];
    for (int i = 0; i < samples.length; i += 3) {
      samples[i] = (byte) (rgb >> 16);
      samples[i + 1] = (byte) (rgb >> 8);
      samples[i + 2] = (byte) rgb;
    }
    return samples;
  }
}
