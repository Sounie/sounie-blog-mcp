package nz.sounie.blogmcp.search.domain.query;

import java.util.List;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.index.PostMatch;

/** Post matches ordered by ranking and cut to the limit. */
public record SearchResults(List<PostMatch> matches) {

  public SearchResults {
    matches = List.copyOf(matches);
  }

  public static SearchResults rank(Stream<PostMatch> matches, ResultLimit limit) {
    return new SearchResults(matches.sorted(PostMatch.RANKING).limit(limit.value()).toList());
  }
}
