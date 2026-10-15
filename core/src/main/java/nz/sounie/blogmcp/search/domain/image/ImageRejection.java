package nz.sounie.blogmcp.search.domain.image;

/**
 * Why an image can never be indexed, so fetching it again is pointless (search.md 3.7). Each reason
 * owns a one-line explanation for the person searching with an image (search.md 3.8).
 */
public enum ImageRejection {
  /** The URL, or a redirect target, is not {@code https}. */
  NOT_HTTPS("it redirects to a URL that is not https"),
  /**
   * An address of the image host, or of a redirect target, is not permitted for the blog. Never
   * produced for a query image while the server is stdio-only.
   */
  ADDRESS_NOT_PERMITTED("its host's address is not one this server may fetch"),
  /** More than three redirects. */
  TOO_MANY_REDIRECTS("it redirects more than 3 times"),
  /** A 4xx status other than 408 and 429, or a status that cannot be followed. */
  HTTP_STATUS("the server answered with an error status"),
  /** Over 10 MiB, or over the image-pixels cap. */
  TOO_LARGE("it is larger than 10 MB or 16 megapixels"),
  /** Not JPEG, PNG or GIF. */
  UNSUPPORTED_TYPE("it is not a JPEG, PNG or GIF image (WebP is not supported)"),
  /** The bytes cannot be decoded as an image. */
  UNDECODABLE("it cannot be read as a JPEG, PNG or GIF image"),
  /** A decoded side is under 64 pixels. */
  TOO_SMALL("it is smaller than 64 pixels on a side");

  private final String explanation;

  ImageRejection(String explanation) {
    this.explanation = explanation;
  }

  /** What the person searching is told about the image, after the image text. */
  public String explanation() {
    return explanation;
  }
}
