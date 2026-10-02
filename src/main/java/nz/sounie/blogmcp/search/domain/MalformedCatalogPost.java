package nz.sounie.blogmcp.search.domain;

/** A published-language payload (event or catalog post state) that search cannot translate. */
public final class MalformedCatalogPost extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public MalformedCatalogPost(String message) {
    super(message);
  }

  /**
   * The value of a published-language field that must be present.
   *
   * @throws MalformedCatalogPost if it is null
   */
  public static <T> T requirePresent(T value, String field) {
    if (value == null) {
      throw new MalformedCatalogPost("The " + field + " is missing");
    }
    return value;
  }
}
