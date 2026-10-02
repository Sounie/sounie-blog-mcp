package nz.sounie.blogmcp.search.application;

import static nz.sounie.blogmcp.search.domain.IndexedPosts.chunkText;
import static nz.sounie.blogmcp.search.domain.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.PostToIndexBuilder.aPost;
import static nz.sounie.blogmcp.search.domain.SearchQueryBuilder.aQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.IndexChange;
import nz.sounie.blogmcp.search.domain.PostId;
import nz.sounie.blogmcp.search.domain.PostMatch;
import nz.sounie.blogmcp.search.domain.PostToIndex;
import nz.sounie.blogmcp.search.domain.PublishedDateRange;
import nz.sounie.blogmcp.search.domain.QueryPassage;
import nz.sounie.blogmcp.search.domain.SearchResults;
import nz.sounie.blogmcp.search.domain.Vectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SearchPostsTest {

  private final SearchContext search = new SearchContext();

  private static List<String> ids(SearchResults results) {
    return results.matches().stream().map(m -> m.postId().external()).toList();
  }

  private void store(PostToIndex post, double... chunkScores) {
    search.index.save(
        indexed(
            post,
            fingerprint('a'),
            IntStream.range(0, chunkScores.length)
                .mapToObj(i -> Vectors.atSimilarity(chunkScores[i], i + 1))
                .toArray(nz.sounie.blogmcp.search.domain.Embedding[]::new)));
  }

  @Test
  @DisplayName("AC-SRCH-22: an empty index returns no results")
  void empty_index_returns_nothing() {
    assertThat(search.searchPosts.search(aQuery("records").build()).matches()).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-22: filters that exclude every post return no results")
  void filters_excluding_everything_return_nothing() {
    store(aPost().id("sounie-wp:1").build(), 0.9);

    assertThat(search.searchPosts.search(aQuery("records").onlySite("elegant").build()).matches())
        .isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-23: hits are grouped by post, ranked by best chunk, snippet is that chunk")
  void groups_and_ranks_by_best_chunk() {
    search.embedder.answerQueriesWith(Vectors.query());
    PostToIndex a = aPost().id("sounie-wp:1").title("A").build();
    PostToIndex b = aPost().id("elegant:2").title("B").build();
    store(a, 0.90, 0.80);
    store(b, 0.85);

    List<PostMatch> matches = search.searchPosts.search(aQuery("records").build()).matches();

    assertThat(matches)
        .extracting(m -> m.postId().external())
        .containsExactly("sounie-wp:1", "elegant:2");
    assertThat(matches.get(0).score().value()).isCloseTo(0.90, within(1e-6));
    assertThat(matches.get(1).score().value()).isCloseTo(0.85, within(1e-6));
    assertThat(matches.get(0).snippet()).isEqualTo(chunkText(a.id(), 0));
  }

  @Test
  @DisplayName("AC-SRCH-24: the limit counts posts, not chunks")
  void limit_counts_posts() {
    search.embedder.answerQueriesWith(Vectors.query());
    IntStream.rangeClosed(1, 25)
        .forEach(i -> store(aPost().id("sounie-wp:" + i).build(), i / 100.0, i / 200.0, i / 300.0));

    List<String> ids = ids(search.searchPosts.search(aQuery("records").limit(20).build()));

    assertThat(ids).hasSize(20).doesNotHaveDuplicates();
    assertThat(ids.getFirst()).isEqualTo("sounie-wp:25");
    assertThat(ids.getLast()).isEqualTo("sounie-wp:6");
  }

  @Test
  @DisplayName("AC-SRCH-25: the same index and query always give the same order")
  void same_query_same_order() {
    // Wiring only: the tie-break permutations live in SearchResultsTest.
    search.embedder.answerQueriesWith(Vectors.query());
    store(aPost().id("sounie-wp:3").build(), 0.7);
    store(aPost().id("elegant:9").build(), 0.8);
    store(aPost().id("sounie-wp:1").build(), 0.6);

    List<String> first = ids(search.searchPosts.search(aQuery("records").build()));

    assertThat(first).containsExactly("elegant:9", "sounie-wp:3", "sounie-wp:1");
    assertThat(ids(search.searchPosts.search(aQuery("records").build()))).isEqualTo(first);
  }

  @Test
  @DisplayName("AC-SRCH-26: the site filter")
  void site_filter() {
    store(aPost().id("sounie-wp:1").build(), 0.9);
    store(aPost().id("elegant:2").build(), 0.8);

    assertThat(ids(search.searchPosts.search(aQuery("records").onlySite("elegant").build())))
        .containsExactly("elegant:2");
    assertThat(ids(search.searchPosts.search(aQuery("records").build())))
        .containsExactlyInAnyOrder("sounie-wp:1", "elegant:2");
    assertThat(ids(search.searchPosts.search(aQuery("records").onlySite("nobody").build())))
        .isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-27: the date filter uses Pacific/Auckland calendar days")
  void date_filter() {
    store(
        aPost().id("sounie-wp:1").publishedAt(Instant.parse("2024-03-31T10:59:59Z")).build(), 0.9);
    store(
        aPost().id("sounie-wp:2").publishedAt(Instant.parse("2024-03-31T11:30:00Z")).build(), 0.9);
    LocalDate april1 = LocalDate.of(2024, 4, 1);

    assertThat(
            ids(
                search.searchPosts.search(
                    aQuery("records").dates(PublishedDateRange.between(april1, april1)).build())))
        .containsExactly("sounie-wp:2");
  }

  @Test
  @DisplayName("AC-SRCH-32: the embedder receives exactly the instruction and the query text")
  void query_is_embedded_with_the_instruction() {
    search.searchPosts.search(aQuery("records in java").build());

    assertThat(search.embedder.queries())
        .containsExactly(
            new QueryPassage(
                "Represent this sentence for searching relevant passages: records in java"));
  }

  @Test
  @DisplayName("AC-SRCH-4: a title-only post can be found; an empty post never appears")
  void title_only_post_is_found_and_empty_post_is_not() {
    search.indexPost.apply(
        new IndexChange.Upsert(aPost().id("sounie-wp:1").title("Hello").body("").build()));
    search.indexPost.apply(
        new IndexChange.Upsert(aPost().id("sounie-wp:2").title(" ").body(" ").build()));

    assertThat(search.index.find(PostId.parse("sounie-wp:2")))
        .hasValueSatisfying(p -> assertThat(p.chunks()).isEmpty());
    assertThat(ids(search.searchPosts.search(aQuery("hello").build())))
        .containsExactly("sounie-wp:1");
  }
}
