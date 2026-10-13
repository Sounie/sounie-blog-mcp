package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import java.util.stream.IntStream;

/**
 * PIL's resampling coefficients for one axis ({@code precompute_coeffs} and {@code
 * normalize_coeffs_8bpc} in {@code Resample.c}): for each output position, the first source sample
 * it reads and its fixed-point bicubic weights. When downscaling, the kernel's support widens by
 * the scale factor, which is what makes the resize antialiased.
 */
final class BicubicAxis {

  /** PIL's fixed-point precision for 8-bit images: 32 − 8 − 2 bits. */
  private static final int PRECISION_BITS = 22;

  private static final double ONE = 1 << PRECISION_BITS;

  /** Half of one in fixed point, so the final shift rounds to nearest. */
  private static final int HALF = 1 << (PRECISION_BITS - 1);

  /** The bicubic kernel's support at scale 1. */
  private static final double SUPPORT = 2.0;

  /** PIL's bicubic parameter. */
  private static final double A = -0.5;

  /** One output position's source window. */
  private record Taps(int first, int[] weights) {}

  private final Taps[] taps;

  private BicubicAxis(Taps[] taps) {
    this.taps = taps;
  }

  /** The coefficients for resampling {@code inSize} samples to {@code outSize}. */
  static BicubicAxis of(int inSize, int outSize) {
    double scale = (double) inSize / outSize;
    double filterScale = Math.max(scale, 1.0);
    return new BicubicAxis(
        IntStream.range(0, outSize)
            .mapToObj(out -> taps(inSize, (out + 0.5) * scale, filterScale))
            .toArray(Taps[]::new));
  }

  /** Mirrors PIL's arithmetic step by step, including its summation order, so weights match. */
  private static Taps taps(int inSize, double center, double filterScale) {
    double support = SUPPORT * filterScale;
    double inverseScale = 1.0 / filterScale;
    int first = Math.max((int) (center - support + 0.5), 0);
    int count = Math.min((int) (center + support + 0.5), inSize) - first;
    double[] weights =
        IntStream.range(0, count)
            .mapToDouble(x -> bicubic((x + first - center + 0.5) * inverseScale))
            .toArray();
    double total = sequentialSum(weights);
    return new Taps(
        first, IntStream.range(0, count).map(x -> toFixedPoint(weights[x] / total)).toArray());
  }

  /** Left to right, as PIL adds; {@code DoubleStream.sum} compensates and can differ. */
  private static double sequentialSum(double[] values) {
    double total = 0.0;
    for (double value : values) {
      total += value;
    }
    return total;
  }

  /** PIL's {@code bicubic_filter}, with a = −0.5. */
  private static double bicubic(double offset) {
    double x = Math.abs(offset);
    if (x < 1.0) {
      return ((A + 2.0) * x - (A + 3.0)) * x * x + 1;
    }
    if (x < 2.0) {
      return (((x - 5) * x + 8) * x - 4) * A;
    }
    return 0.0;
  }

  /** Rounds half away from zero, as {@code normalize_coeffs_8bpc} does. */
  private static int toFixedPoint(double weight) {
    return (int) (weight * ONE + Math.copySign(0.5, weight));
  }

  /**
   * Output sample {@code out}: the weighted sum of the source samples {@code data[base + (first +
   * t) × stride]}, rounded and clamped to 0..255 as PIL's {@code clip8} does.
   */
  byte sample(int out, byte[] data, int base, int stride) {
    Taps window = taps[out];
    int sum = HALF;
    for (int t = 0; t < window.weights().length; t++) {
      sum += Byte.toUnsignedInt(data[base + (window.first() + t) * stride]) * window.weights()[t];
    }
    return (byte) Math.clamp(sum >> PRECISION_BITS, 0, 255);
  }

  int outSize() {
    return taps.length;
  }
}
