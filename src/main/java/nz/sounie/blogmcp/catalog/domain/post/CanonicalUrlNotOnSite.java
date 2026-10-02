package nz.sounie.blogmcp.catalog.domain.post;

/** A canonical URL that is not absolute {@code https} on the site host. */
public final class CanonicalUrlNotOnSite extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public CanonicalUrlNotOnSite(String message) {
    super(message);
  }
}
