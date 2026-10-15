package nz.sounie.blogmcp.search.application;

import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.InMemoryImageSource;
import nz.sounie.blogmcp.search.adapter.out.InMemoryQueryImageSource;
import nz.sounie.blogmcp.search.adapter.out.InMemoryVectorIndex;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.image.ImageQuery;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import nz.sounie.blogmcp.search.domain.index.IndexedChunk;
import nz.sounie.blogmcp.search.domain.index.IndexedImage;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.index.PostMatch;
import nz.sounie.blogmcp.search.domain.index.PostToIndex;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import nz.sounie.blogmcp.search.domain.query.ImageSearchQuery;
import nz.sounie.blogmcp.search.domain.query.PublishedDateRange;
import nz.sounie.blogmcp.search.domain.query.ResultLimit;
import nz.sounie.blogmcp.search.domain.query.SearchScope;
import nz.sounie.blogmcp.search.domain.query.SiteFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link FindPostsLikeImage} over the real in-memory index, the {@link FakeEmbedder} (our {@code
 * ImageEmbedder} port) and the {@link InMemoryQueryImageSource} fake. Filtering, best entry and
 * ranking are {@code SearchScope}'s and are not repeated here.
 */
class FindPostsLikeImageTest {

  private static final String QUERY_URL = "https://images.example.com/swan.jpg";
  private static final String SWAN_URL = "https://x.example/swan.jpg";
  private static final ImagePixels QUERY_PIXELS = InMemoryImageSource.pixels(7);

  private final InMemoryVectorIndex index = new InMemoryVectorIndex();
  private final FakeEmbedder embedder =
      new FakeEmbedder().assignImage(QUERY_PIXELS, Vectors.query());
  private final InMemoryQueryImageSource queryImages = new InMemoryQueryImageSource();
  private final FindPostsLikeImage find = new FindPostsLikeImage(index, embedder, queryImages);

  private static ImageSearchQuery imageQuery(SiteFilter site, int limit) {
    return new ImageSearchQuery(
        new ImageQuery.RemoteImage(URI.create(QUERY_URL)),
        new SearchScope(site, PublishedDateRange.unbounded(), ResultLimit.of(limit)));
  }

  private static ImageSearchQuery anyImageQuery() {
    return imageQuery(new SiteFilter.AnySite(), 10);
  }

  private static SiteFilter sounieWp() {
    return new SiteFilter.OnlySite(new SiteId("sounie-wp"));
  }

  /** A: image 0.95, chunk 0.40. B: chunk 0.70. C (another site): chunk 0.99. */
  private void givenPostsAbAndC() {
    PostToIndex a =
        aPost().id("sounie-wp:1").publishedAt(Instant.parse("2024-05-01T00:00:00Z")).build();
    index.save(
        IndexedPost.restore(
            a.id(),
            a.metadata(),
            fingerprint('a'),
            List.of(new IndexedChunk(0, "a walk by the lake", Vectors.atSimilarity(384, 0.40, 1))),
            List.of(
                new IndexedImage(
                    0,
                    URI.create(SWAN_URL),
                    "A black swan among the reeds",
                    Vectors.atSimilarity(384, 0.95, 2)))));
    PostToIndex b =
        aPost().id("sounie-wp:2").publishedAt(Instant.parse("2024-06-01T00:00:00Z")).build();
    index.save(
        IndexedPost.restore(
            b.id(),
            b.metadata(),
            fingerprint('b'),
            List.of(new IndexedChunk(0, "black swans", Vectors.atSimilarity(384, 0.70, 3)))));
    PostToIndex c = aPost().id("elegant:3").build();
    index.save(
        IndexedPost.restore(
            c.id(),
            c.metadata(),
            fingerprint('c'),
            List.of(new IndexedChunk(0, "records", Vectors.atSimilarity(384, 0.99, 4)))));
  }

  private static List<PostMatch> ranked(ImageSearch outcome) {
    assertThat(outcome).isInstanceOf(ImageSearch.Ranked.class);
    return ((ImageSearch.Ranked) outcome).results().matches();
  }

  @Nested
  class Ranking {

    @Test
    @DisplayName(
        "AC-SRCH-82: an image query matches an image entry and a text chunk, ranked like a text"
            + " query")
    void ranks_posts_like_a_text_query() {
      givenPostsAbAndC();
      queryImages.fetching(QUERY_URL, QUERY_PIXELS);

      List<PostMatch> matches = ranked(find.find(imageQuery(sounieWp(), 10)));

      assertThat(matches)
          .extracting(m -> m.postId().external())
          .containsExactly("sounie-wp:1", "sounie-wp:2");
      assertThat(matches.get(0).score().value()).isCloseTo(0.95, within(1e-6));
      assertThat(matches.get(0).snippet().text()).isEqualTo("A black swan among the reeds");
      assertThat(matches.get(0).matchedImage()).contains(URI.create(SWAN_URL));
      assertThat(matches.get(1).score().value()).isCloseTo(0.70, within(1e-6));
      assertThat(matches.get(1).matchedImage()).isEmpty();
    }

    @Test
    @DisplayName(
        "AC-SRCH-82: the fetched pixels are embedded once as an image, never as a text query")
    void embeds_the_fetched_pixels_once_with_the_image_embedder() {
      givenPostsAbAndC();
      queryImages.fetching(QUERY_URL, QUERY_PIXELS);

      find.find(anyImageQuery());

      assertThat(queryImages.urlsFetched()).containsExactly(URI.create(QUERY_URL));
      assertThat(embedder.imageCalls()).containsExactly(QUERY_PIXELS);
      assertThat(embedder.queries()).isEmpty();
    }

    @Test
    @DisplayName("AC-SRCH-82: the scope's limit applies")
    void the_limit_applies() {
      givenPostsAbAndC();
      queryImages.fetching(QUERY_URL, QUERY_PIXELS);

      assertThat(ranked(find.find(imageQuery(sounieWp(), 1))))
          .extracting(m -> m.postId().external())
          .containsExactly("sounie-wp:1");
    }

    @Test
    @DisplayName("AC-SRCH-82: an empty index is a ranking with no matches")
    void an_empty_index_ranks_nothing() {
      queryImages.fetching(QUERY_URL, QUERY_PIXELS);

      assertThat(ranked(find.find(anyImageQuery()))).isEmpty();
    }
  }

  @Nested
  class RefusedOrUnavailable {

    @Test
    @DisplayName("AC-SRCH-83: a rejected query image is refused, and nothing is embedded")
    void a_rejected_image_is_refused() {
      givenPostsAbAndC();
      queryImages.rejecting(QUERY_URL, ImageRejection.TOO_LARGE);

      ImageSearch outcome = find.find(anyImageQuery());

      assertThat(outcome).isEqualTo(new ImageSearch.ImageRefused(ImageRejection.TOO_LARGE));
      assertThat(embedder.imageCalls()).isEmpty();
    }

    @Test
    @DisplayName("AC-SRCH-83: an unavailable query image is reported with its detail")
    void an_unavailable_image_is_reported() {
      givenPostsAbAndC();
      queryImages.unavailable(QUERY_URL, "connect timed out");

      ImageSearch outcome = find.find(anyImageQuery());

      assertThat(outcome).isEqualTo(new ImageSearch.ImageUnavailable("connect timed out"));
      assertThat(embedder.imageCalls()).isEmpty();
    }

    @Test
    @DisplayName("AC-SRCH-83: a failing image embedder propagates EmbedderUnavailable unchanged")
    void a_failing_embedder_propagates() {
      givenPostsAbAndC();
      queryImages.fetching(QUERY_URL, QUERY_PIXELS);
      embedder.failingForImage(QUERY_PIXELS);

      Throwable thrown = catchThrowable(() -> find.find(anyImageQuery()));

      assertThat(thrown)
          .isInstanceOf(EmbedderUnavailable.class)
          .hasMessage("fake image embedder failure");
    }
  }
}
