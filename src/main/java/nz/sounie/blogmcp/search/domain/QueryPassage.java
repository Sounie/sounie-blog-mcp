package nz.sounie.blogmcp.search.domain;

import java.util.Objects;

/** The exact text embedded for a query: the query instruction followed by the query text. */
public record QueryPassage(String text) {

  public static final String INSTRUCTION =
      "Represent this sentence for searching relevant passages: ";

  public QueryPassage {
    Objects.requireNonNull(text, "text");
  }

  public static QueryPassage of(QueryText query) {
    return new QueryPassage(INSTRUCTION + query.value());
  }
}
