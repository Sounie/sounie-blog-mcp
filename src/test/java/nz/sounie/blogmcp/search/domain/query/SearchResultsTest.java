package nz.sounie.blogmcp.search.domain.query;

import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.embedding.Similarity;
import nz.sounie.blogmcp.search.domain.index.PostMatch;
import nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchResultsTest {

  private static final Instant EARLIER = Instant.parse("2024-01-01T00:00:00Z");
  private static final Instant LATER = Instant.parse("2024-06-01T00:00:00Z");

  private static PostMatch match(String id, double score, Instant publishedAt) {
    PostToIndexBuilder post = aPost().id(id).publishedAt(publishedAt);
    return new PostMatch(post.postId(), post.metadata(), new Similarity(score), id + " snippet");
  }

  private static List<String> ids(SearchResults results) {
    return results.matches().stream().map(m -> m.postId().external()).toList();
  }

  @Test
  @DisplayName("AC-SRCH-23: posts are ranked by score, highest first")
  void ranks_by_score() {
    SearchResults results =
        SearchResults.rank(
            Stream.of(match("sounie-wp:2", 0.85, EARLIER), match("sounie-wp:1", 0.90, EARLIER)),
            ResultLimit.of(10));

    assertThat(ids(results)).containsExactly("sounie-wp:1", "sounie-wp:2");
  }

  @Test
  @DisplayName("AC-SRCH-25: equal scores rank the more recently published first")
  void ties_go_to_the_more_recent_post() {
    SearchResults results =
        SearchResults.rank(
            Stream.of(match("elegant:9", 0.8, EARLIER), match("sounie-wp:3", 0.8, LATER)),
            ResultLimit.of(10));

    assertThat(ids(results)).containsExactly("sounie-wp:3", "elegant:9");
  }

  @Test
  @DisplayName("AC-SRCH-25: equal scores and dates rank by post ID ascending")
  void then_by_post_id() {
    SearchResults results =
        SearchResults.rank(
            Stream.of(
                match("sounie-wp:3", 0.8, EARLIER),
                match("sounie-wp:10", 0.8, EARLIER),
                match("elegant:9", 0.8, EARLIER)),
            ResultLimit.of(10));

    assertThat(ids(results)).containsExactly("elegant:9", "sounie-wp:10", "sounie-wp:3");
  }

  @Test
  @DisplayName("AC-SRCH-24: the limit counts posts")
  void cuts_to_the_limit() {
    List<PostMatch> matches =
        IntStream.rangeClosed(1, 25)
            .mapToObj(i -> match("sounie-wp:" + i, i / 100.0, EARLIER))
            .toList();

    SearchResults results = SearchResults.rank(matches.stream(), ResultLimit.of(20));

    assertThat(results.matches()).hasSize(20);
    assertThat(ids(results).getFirst()).isEqualTo("sounie-wp:25");
    assertThat(ids(results).getLast()).isEqualTo("sounie-wp:6");
  }

  @Test
  void fewer_matches_than_the_limit_are_all_returned() {
    assertThat(
            SearchResults.rank(Stream.of(match("sounie-wp:1", 0.5, EARLIER)), ResultLimit.of(10))
                .matches())
        .hasSize(1);
  }

  @Test
  @DisplayName("AC-SRCH-22: no matches give empty results")
  void no_matches_give_empty_results() {
    assertThat(SearchResults.rank(Stream.empty(), ResultLimit.of(10)).matches()).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-25: the order does not depend on input order")
  void order_is_deterministic() {
    List<PostMatch> matches =
        new ArrayList<>(
            List.of(
                match("sounie-wp:3", 0.8, EARLIER),
                match("elegant:9", 0.8, EARLIER),
                match("elegant:1", 0.8, LATER),
                match("sounie-wp:7", 0.9, EARLIER)));
    List<String> first = ids(SearchResults.rank(matches.stream(), ResultLimit.of(10)));

    Collections.shuffle(matches, new Random(42));

    assertThat(ids(SearchResults.rank(matches.stream(), ResultLimit.of(10))))
        .containsExactlyElementsOf(first)
        .containsExactly("sounie-wp:7", "elegant:1", "elegant:9", "sounie-wp:3");
  }
}
