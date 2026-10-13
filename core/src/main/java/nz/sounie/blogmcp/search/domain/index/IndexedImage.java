package nz.sounie.blogmcp.search.domain.index;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;

/**
 * One embedded post image: its image index (its position among the post's images, so skipped images
 * leave gaps), URL, image description and embedding.
 *
 * <p>The compact constructor throws {@link InvalidIndexedPost} if the URL is not {@code https} with
 * a host or the description is blank (invariant 7).
 */
public record IndexedImage(int index, URI url, String description, Embedding embedding)
    implements IndexEntry {

  public IndexedImage {
    Objects.requireNonNull(url, "url");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(embedding, "embedding");
    requireHttpsWithHost(url);
    requireDescription(description);
  }

  private static void requireHttpsWithHost(URI url) {
    if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null) {
      throw new InvalidIndexedPost("Indexed image URL is not https with a host: " + url);
    }
  }

  private static void requireDescription(String description) {
    if (description.isBlank()) {
      throw new InvalidIndexedPost("Indexed image description is blank");
    }
  }

  @Override
  public Kind kind() {
    return Kind.IMAGE;
  }

  /** The start of the image description. */
  @Override
  public Snippet snippet() {
    return Snippet.of(description);
  }

  @Override
  public Optional<URI> matchedImage() {
    return Optional.of(url);
  }
}
