package nz.sounie.blogmcp.search.domain;

import java.util.List;
import java.util.stream.Stream;

/** Post matches ordered by ranking and cut to the limit. */
public record SearchResults(List<PostMatch> matches) {

  public static SearchResults rank(Stream<PostMatch> matches, ResultLimit limit) {
    throw new UnsupportedOperationException("not implemented");
  }
}
