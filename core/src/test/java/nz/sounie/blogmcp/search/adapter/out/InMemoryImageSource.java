package nz.sounie.blogmcp.search.adapter.out;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import nz.sounie.blogmcp.search.domain.image.ImageFetch;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import nz.sounie.blogmcp.search.domain.image.ImageSource;

/**
 * Fake of our {@link ImageSource} port: returns the {@link ImageFetch} scripted for each image URL
 * and records every call. An unscripted URL is a mistake in the test, so it fails loudly.
 */
public final class InMemoryImageSource implements ImageSource {

  /** One call: the image URL and the post's canonical URL it was fetched for. */
  public record Call(URI imageUrl, URI postUrl) {}

  private final Map<URI, ImageFetch> scripted = new ConcurrentHashMap<>();
  private final List<Call> calls = new CopyOnWriteArrayList<>();

  @Override
  public ImageFetch fetch(URI imageUrl, URI postUrl) {
    calls.add(new Call(imageUrl, postUrl));
    ImageFetch fetch = scripted.get(imageUrl);
    if (fetch == null) {
      throw new IllegalStateException("No fetch scripted for " + imageUrl);
    }
    return fetch;
  }

  /** The image at this URL fetches as these pixels. */
  public InMemoryImageSource fetching(String imageUrl, ImagePixels pixels) {
    return scripting(imageUrl, new ImageFetch.Fetched(pixels));
  }

  /** The image at this URL is rejected for this reason. */
  public InMemoryImageSource rejecting(String imageUrl, ImageRejection reason) {
    return scripting(imageUrl, new ImageFetch.Rejected(reason));
  }

  /** The image at this URL is unavailable (a transient failure). */
  public InMemoryImageSource unavailable(String imageUrl) {
    return scripting(imageUrl, new ImageFetch.Unavailable("fake: server busy"));
  }

  private InMemoryImageSource scripting(String imageUrl, ImageFetch fetch) {
    scripted.put(URI.create(imageUrl), fetch);
    return this;
  }

  public List<Call> calls() {
    return List.copyOf(calls);
  }

  public List<URI> fetchedUrls() {
    return calls.stream().map(Call::imageUrl).toList();
  }

  public void clearCalls() {
    calls.clear();
  }

  /**
   * The pixels of an image file on the test classpath, decoded exactly as a fetched post image is
   * (real ImageIO through {@code ImageDecoding}), for tests that index a real photograph without
   * HTTP.
   */
  public static ImagePixels pixelsOfResource(String resource) {
    try (java.io.InputStream in = InMemoryImageSource.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new IllegalStateException("No test resource " + resource);
      }
      return switch (ImageDecoding.decode(in.readAllBytes())) {
        case ImageFetch.Fetched fetched -> fetched.pixels();
        case ImageFetch rejectedOrUnavailable ->
            throw new IllegalStateException(
                resource + " does not decode: " + rejectedOrUnavailable);
      };
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  /**
   * Distinct, valid 64 × 64 pixels for a seed, so each test image embeds to its own vector in
   * {@link FakeEmbedder}.
   */
  public static ImagePixels pixels(int seed) {
    byte[] rgb = new byte[64 * 64 * 3];
    java.util.Arrays.fill(rgb, (byte) seed);
    return new ImagePixels(64, 64, rgb);
  }
}
