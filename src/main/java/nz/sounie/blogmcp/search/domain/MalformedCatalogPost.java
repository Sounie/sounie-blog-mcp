package nz.sounie.blogmcp.search.domain;

/** A published-language payload (event or catalog post state) that search cannot translate. */
public final class MalformedCatalogPost extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MalformedCatalogPost(String message) {
    super(message);
  }
}
