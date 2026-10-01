package nz.sounie.blogmcp.search.domain;

/** The exact text embedded for a query: the query instruction followed by the query text. */
public record QueryPassage(String text) {

  public static final String INSTRUCTION =
      "Represent this sentence for searching relevant passages: ";

  public static QueryPassage of(QueryText query) {
    throw new UnsupportedOperationException("not implemented");
  }
}
