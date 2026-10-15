package nz.sounie.blogmcp.search.domain.query;

import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.index.IndexedChunk;
import nz.sounie.blogmcp.search.domain.index.IndexedImage;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.index.PostMatch;
import nz.sounie.blogmcp.search.domain.index.PostToIndex;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link SearchScope} composes decisions owned and tested elsewhere: the filters ({@code
 * SearchFiltersTest}), the best entry ({@code IndexedPostTest}, {@code EntryHitTest}) and the
 * ranking and limit ({@code SearchResultsTest}). One test per collaborator wiring.
 */
class SearchScopeTest {

  private static final SearchScope EVERYTHING =
      new SearchScope(new SiteFilter.AnySite(), PublishedDateRange.unbounded(), ResultLimit.of(20));

  private static IndexedPost withChunks(PostToIndex post, double... similarities) {
    List<IndexedChunk> chunks =
        java.util.stream.IntStream.range(0, similarities.length)
            .mapToObj(
                i -> new IndexedChunk(i, "chunk " + i, Vectors.atSimilarity(384, similarities[i])))
            .toList();
    return IndexedPost.restore(post.id(), post.metadata(), fingerprint('a'), chunks);
  }

  private static List<String> ids(SearchResults results) {
    return results.matches().stream().map(m -> m.postId().external()).toList();
  }

  @Test
  @DisplayName("AC-SRCH-82: posts outside the site filter or the date range are left out")
  void leaves_out_posts_outside_the_scope() {
    IndexedPost inScope =
        withChunks(
            aPost().id("sounie-wp:1").publishedAt(Instant.parse("2024-05-01T00:00:00Z")).build(),
            0.5);
    IndexedPost otherSite =
        withChunks(
            aPost().id("elegant:2").publishedAt(Instant.parse("2024-05-01T00:00:00Z")).build(),
            0.9);
    IndexedPost tooEarly =
        withChunks(
            aPost().id("sounie-wp:3").publishedAt(Instant.parse("2023-05-01T00:00:00Z")).build(),
            0.9);
    SearchScope scope =
        new SearchScope(
            new SiteFilter.OnlySite(new SiteId("sounie-wp")),
            PublishedDateRange.from(LocalDate.of(2024, 1, 1)),
            ResultLimit.of(10));

    SearchResults results = scope.rank(Stream.of(inScope, otherSite, tooEarly), Vectors.query());

    assertThat(ids(results)).containsExactly("sounie-wp:1");
  }

  @Test
  @DisplayName(
      "AC-SRCH-82: each post scores as its best entry, chunk or image, and posts are ranked")
  void ranks_posts_by_their_best_entry() {
    PostToIndex a = aPost().id("sounie-wp:1").build();
    IndexedPost withImage =
        IndexedPost.restore(
            a.id(),
            a.metadata(),
            fingerprint('a'),
            List.of(new IndexedChunk(0, "about birds", Vectors.atSimilarity(384, 0.40, 1))),
            List.of(
                new IndexedImage(
                    0,
                    URI.create("https://x.example/swan.jpg"),
                    "Black swan",
                    Vectors.atSimilarity(384, 0.95, 2))));
    IndexedPost textOnly = withChunks(aPost().id("sounie-wp:2").build(), 0.70, 0.30);

    List<PostMatch> matches =
        EVERYTHING.rank(Stream.of(textOnly, withImage), Vectors.query()).matches();

    assertThat(matches)
        .extracting(m -> m.postId().external())
        .containsExactly("sounie-wp:1", "sounie-wp:2");
    assertThat(matches.get(0).score().value()).isCloseTo(0.95, within(1e-6));
    assertThat(matches.get(0).matchedImage()).contains(URI.create("https://x.example/swan.jpg"));
    assertThat(matches.get(1).score().value()).isCloseTo(0.70, within(1e-6));
  }

  @Test
  @DisplayName("AC-SRCH-82: the limit cuts the ranked posts")
  void cuts_to_the_limit() {
    SearchScope topOne =
        new SearchScope(
            new SiteFilter.AnySite(), PublishedDateRange.unbounded(), ResultLimit.of(1));

    SearchResults results =
        topOne.rank(
            Stream.of(
                withChunks(aPost().id("sounie-wp:1").build(), 0.6),
                withChunks(aPost().id("sounie-wp:2").build(), 0.8)),
            Vectors.query());

    assertThat(ids(results)).containsExactly("sounie-wp:2");
  }

  @Test
  @DisplayName("AC-SRCH-82: a text query's scope is its site filter, date range and limit")
  void a_search_query_exposes_its_scope() {
    SiteFilter site = new SiteFilter.OnlySite(new SiteId("elegant"));
    PublishedDateRange dates = PublishedDateRange.unbounded();
    SearchQuery query = new SearchQuery(new QueryText("swans"), site, dates, ResultLimit.of(5));

    assertThat(query.scope()).isEqualTo(new SearchScope(site, dates, ResultLimit.of(5)));
  }
}
