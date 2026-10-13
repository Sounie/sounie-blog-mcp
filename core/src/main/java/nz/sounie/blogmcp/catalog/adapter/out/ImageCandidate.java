package nz.sounie.blogmcp.catalog.adapter.out;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.post.PostImage;
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
    return new ImageCandidate(
        postUrl,
        img.attr("src"),
        img.attr("srcset"),
        img.attr("class"),
        img.attr("width"),
        img.attr("height"),
        img.attr("alt").replaceAll("\\s+", " ").strip(),
        captionOf(img));
  }

  /**
   * The {@link Srcset} choice, or else {@code src}, resolved against the post URL. Empty when
   * neither yields a URL, or the URL does not parse. Never upgrades {@code http}.
   */
  Optional<URI> resolvedUrl() {
    return new Srcset(srcset)
        .chosenUrl()
        .or(() -> Optional.of(src.strip()).filter(url -> !url.isEmpty()))
        .flatMap(this::resolved);
  }

  /**
   * The post image this candidate describes, if it has a URL.
   *
   * @throws IllegalArgumentException if the URL is not absolute https, which {@link
   *     ImageExclusion#NOT_HTTPS} excludes beforehand
   */
  Optional<PostImage> toPostImage() {
    return resolvedUrl().map(url -> new PostImage(url, altText, caption));
  }

  private Optional<URI> resolved(String url) {
    try {
      return Optional.of(postUrl.value().resolve(new URI(url)));
    } catch (URISyntaxException e) {
      return Optional.empty();
    }
  }

  private static String captionOf(Element img) {
    return Optional.ofNullable(img.closest("figure"))
        .flatMap(ImageCandidate::figcaptionOf)
        .map(Element::text)
        .orElse("");
  }

  /** The figure's own {@code <figcaption>}, not one of a nested figure. */
  private static Optional<Element> figcaptionOf(Element figure) {
    return figure.children().stream().filter(child -> child.nameIs("figcaption")).findFirst();
  }
}
