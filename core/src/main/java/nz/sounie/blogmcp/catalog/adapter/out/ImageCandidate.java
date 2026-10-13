package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import org.jsoup.nodes.Element;

/**
 * The attributes of one {@code <img>} in a post's content, as written (empty when absent), plus the
 * post's canonical URL that relative image URLs resolve against.
 *
 * @param classNames the {@code class} attribute
 * @param altText the {@code alt} text, entities decoded and whitespace collapsed
 * @param caption the text of the {@code <figcaption>} of the nearest enclosing {@code <figure>},
 *     collapsed to one line; empty outside a figure
 */
record ImageCandidate(
    CanonicalUrl postUrl,
    String src,
    String srcset,
    String classNames,
    String width,
    String height,
    String altText,
    String caption) {

  /** Reads the candidate from an {@code <img>} element. */
  static ImageCandidate of(Element img, CanonicalUrl postUrl) {
    throw new UnsupportedOperationException("ImageCandidate.of is not implemented yet");
  }

  /**
   * The {@link Srcset} choice, or else {@code src}, resolved against the post URL. Empty when
   * neither yields a URL, or the URL does not parse. Never upgrades {@code http}.
   */
  Optional<URI> resolvedUrl() {
    throw new UnsupportedOperationException("ImageCandidate.resolvedUrl is not implemented yet");
  }
}
