package nz.sounie.blogmcp.search.domain.image;

/** Why an image can never be indexed, so fetching it again is pointless (search.md 3.7). */
public enum ImageRejection {
  /** The URL, or a redirect target, is not {@code https}. */
  NOT_HTTPS,
  /** An address of the image host, or of a redirect target, is not permitted for the blog. */
  ADDRESS_NOT_PERMITTED,
  /** More than three redirects. */
  TOO_MANY_REDIRECTS,
  /** A 4xx status other than 408 and 429, or a status that cannot be followed. */
  HTTP_STATUS,
  /** Over 10 MiB, or over the image-pixels cap. */
  TOO_LARGE,
  /** Not JPEG, PNG or GIF. */
  UNSUPPORTED_TYPE,
  /** The bytes cannot be decoded as an image. */
  UNDECODABLE,
  /** A decoded side is under 64 pixels. */
  TOO_SMALL
}
