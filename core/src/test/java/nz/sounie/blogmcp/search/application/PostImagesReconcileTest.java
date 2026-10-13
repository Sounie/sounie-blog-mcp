package nz.sounie.blogmcp.search.application;

import static nz.sounie.blogmcp.search.adapter.out.InMemoryImageSource.pixels;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static nz.sounie.blogmcp.search.domain.query.SearchQueryBuilder.aQuery;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.Map;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.InMemoryImageSource;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import nz.sounie.blogmcp.search.domain.image.PostImage;
import nz.sounie.blogmcp.search.domain.index.IndexChange;
import nz.sounie.blogmcp.search.domain.index.IndexOutcome;
import nz.sounie.blogmcp.search.domain.index.IndexedImage;
import nz.sounie.blogmcp.search.domain.index.PostMatch;
import nz.sounie.blogmcp.search.domain.index.PostToIndex;
import nz.sounie.blogmcp.search.domain.text.Words;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Image indexing end to end through the use cases: publish through {@link IndexPost}, search
 * through {@link SearchPosts}, retry through {@link ReconcileIndex}. The fetch-outcome rules
 * themselves are tested on the domain ({@code PostIndexerImagesTest}).
 */
class PostImagesReconcileTest {

  private static final int DIMENSION = 384;
  private static final String A_JPG = "https://x.example/a.jpg";
  private static final String B_PNG = "https://x.example/b.png";
  private static final String PIXEL_GIF = "https://x.example/pixel.gif";

  private final FakeEmbedder embedder = new FakeEmbedder("model-r1", DIMENSION);
  private final InMemoryImageSource images = new InMemoryImageSource();
  private final SearchContext search = SearchContext.withImages(embedder, images);

  private IndexOutcome publish(PostToIndex post) {
    return search.indexPost.apply(new IndexChange.Upsert(post));
  }

  private IndexOutcome reconcile(PostToIndex post) {
    search.catalog.holding(post);
    Map<?, IndexOutcome> outcomes = search.reconcile.run().outcomes();
    assertThat(outcomes).hasSize(1);
    return outcomes.values().iterator().next();
  }

  private PostMatch bestMatchFor(String queryText) {
    return search.searchPosts.search(aQuery(queryText).build()).matches().getFirst();
  }

  @Test
  @DisplayName("AC-SRCH-71: a reconcile after a rejected image is UNCHANGED and fetches nothing")
  void a_reconcile_does_not_refetch_a_rejected_image() {
    images.fetching(A_JPG, pixels(1)).rejecting(PIXEL_GIF, ImageRejection.TOO_SMALL);
    PostToIndex post =
        aPost()
            .id("sounie-wp:1")
            .words(120)
            .images(PostImage.of(A_JPG, "Swan", ""), PostImage.of(PIXEL_GIF, "", ""))
            .build();
    assertThat(publish(post)).isEqualTo(IndexOutcome.ADDED);
    images.clearCalls();

    assertThat(reconcile(post)).isEqualTo(IndexOutcome.UNCHANGED);
    assertThat(images.calls()).isEmpty();
  }

  @Test
  @DisplayName(
      "AC-SRCH-72: a post with an unavailable image is searchable by text and by its fetched"
          + " image, and reconciles retry the image until it is settled")
  void reconciles_retry_an_unavailable_image() {
    Embedding swan = Vectors.axis(DIMENSION, 11);
    images.fetching(A_JPG, pixels(1)).unavailable(B_PNG);
    embedder.assignImage(pixels(1), swan);
    PostToIndex post =
        aPost()
            .id("sounie-wp:1")
            .title("Swans")
            .words(120)
            .images(PostImage.of(A_JPG, "Swan", ""), PostImage.of(B_PNG, "", "Grapes"))
            .build();

    assertThat(publish(post)).isEqualTo(IndexOutcome.ADDED);
    assertThat(bestMatchFor(Words.numbered(20)).matchedImage()).isEmpty();
    embedder.answerQueriesWith(swan);
    assertThat(bestMatchFor("black swan").matchedImage()).contains(URI.create(A_JPG));

    assertThat(reconcile(post)).isEqualTo(IndexOutcome.RE_EMBEDDED);
    assertThat(search.index.find(post.id()).orElseThrow().images())
        .extracting(IndexedImage::url)
        .containsExactly(URI.create(A_JPG));

    images.fetching(B_PNG, pixels(2));
    assertThat(reconcile(post)).isEqualTo(IndexOutcome.RE_EMBEDDED);
    assertThat(search.index.find(post.id()).orElseThrow().images())
        .extracting(IndexedImage::index)
        .containsExactly(0, 1);

    assertThat(reconcile(post)).isEqualTo(IndexOutcome.UNCHANGED);
  }
}
