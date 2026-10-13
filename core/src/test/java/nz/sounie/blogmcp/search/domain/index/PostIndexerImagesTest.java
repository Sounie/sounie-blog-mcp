package nz.sounie.blogmcp.search.domain.index;

import static nz.sounie.blogmcp.search.adapter.out.InMemoryImageSource.pixels;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.net.URI;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.FakeTokenCounter;
import nz.sounie.blogmcp.search.adapter.out.InMemoryImageSource;
import nz.sounie.blogmcp.search.adapter.out.InMemoryVectorIndex;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.embedding.Vectors;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import nz.sounie.blogmcp.search.domain.image.PostImage;
import nz.sounie.blogmcp.search.domain.text.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.text.Passage;
import nz.sounie.blogmcp.search.domain.text.PassageComposition;
import nz.sounie.blogmcp.search.domain.text.WordSequence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Indexing a post's images (search.md 3.7): what each {@link ImageIndexing} strategy does through
 * {@link PostIndexer}, and how pending images are retried by the next upsert.
 */
class PostIndexerImagesTest {

  private static final int DIMENSION = 384;
  private static final String POST_URL = "https://blog2.sounie.nz/swans/";
  private static final String A_JPG = "https://x.example/a.jpg";
  private static final String B_PNG = "https://x.example/b.png";
  private static final String PIXEL_GIF = "https://x.example/pixel.gif";
  private static final PostImage A = PostImage.of(A_JPG, "Swan", "");
  private static final PostImage B = PostImage.of(B_PNG, "", "Grapes");

  private final FakeEmbedder embedder = new FakeEmbedder("fake-model", DIMENSION);
  private final InMemoryImageSource images = new InMemoryImageSource();
  private final InMemoryVectorIndex index = new InMemoryVectorIndex();

  private final PostIndexer textOnly =
      new PostIndexer(
          ChunkingPolicy.standard(),
          PassageComposition.standard(),
          FakeTokenCounter.perWord(1),
          embedder);
  private final PostIndexer withImages =
      textOnly.withImageIndexing(ImageIndexing.withImages(images, embedder));

  private static PostToIndexBuilder swans() {
    return aPost().id("sounie-wp:1").title("Swans").words(120).url(POST_URL);
  }

  private IndexOutcome upsert(PostIndexer indexer, PostToIndex post) {
    return new IndexChange.Upsert(post).applyTo(new IndexWork(index, indexer));
  }

  private IndexedPost stored(PostToIndex post) {
    return index.find(post.id()).orElseThrow();
  }

  static Stream<Arguments> imageRevisions() {
    return Stream.of(
        Arguments.of("adds b.png", new PostImage[] {A, B}),
        Arguments.of("removes a.jpg", new PostImage[] {}),
        Arguments.of(
            "changes a.jpg's alt text", new PostImage[] {PostImage.of(A_JPG, "Black swan", "")}));
  }

  @Nested
  class TextOnly {

    private final PostToIndex post = swans().images(A, B).build();

    @Test
    @DisplayName("AC-SRCH-68: text-only indexing never fetches and records no images")
    void never_fetches_and_records_no_images() {
      IndexedPost indexed = textOnly.index(post);

      assertThat(indexed.images()).isEmpty();
      assertThat(indexed.chunks()).hasSize(1);
      assertThat(images.calls()).isEmpty();
      assertThat(embedder.imageCalls()).isEmpty();
    }

    @Test
    @DisplayName("AC-SRCH-68: recipe and fingerprint are those of the code before phase 3")
    void recipe_and_fingerprint_are_unchanged_by_images() {
      IndexRecipe beforePhase3 =
          IndexRecipe.of("fake-model", ChunkingPolicy.standard(), PassageComposition.standard());

      assertThat(textOnly.recipe()).isEqualTo(beforePhase3);
      assertThat(textOnly.fingerprintOf(post))
          .isEqualTo(textOnly.fingerprintOf(swans().build()))
          .isEqualTo(
              ContentFingerprint.of(beforePhase3, post.title(), WordSequence.of(post.body())));
      assertThat(textOnly.index(post).fingerprint()).isEqualTo(textOnly.fingerprintOf(post));
    }

    @Test
    @DisplayName("AC-SRCH-68: an images-only revision is UNCHANGED, with no embedder call")
    void an_images_only_revision_is_unchanged() {
      upsert(textOnly, swans().build());
      embedder.clearCalls();

      IndexOutcome outcome = upsert(textOnly, post);

      assertThat(outcome).isEqualTo(IndexOutcome.UNCHANGED);
      assertThat(embedder.passageCallCount()).isZero();
      assertThat(images.calls()).isEmpty();
    }
  }

  @Nested
  class WithImages {

    @Test
    @DisplayName("AC-SRCH-69: each fetched image is its own entry, embedded alone")
    void each_fetched_image_is_its_own_entry() {
      Embedding swanVector = Vectors.axis(DIMENSION, 11);
      Embedding grapesVector = Vectors.axis(DIMENSION, 12);
      images.fetching(A_JPG, pixels(1)).fetching(B_PNG, pixels(2));
      embedder.assignImage(pixels(1), swanVector).assignImage(pixels(2), grapesVector);
      PostToIndex post = swans().images(A, B).build();

      IndexOutcome outcome = upsert(withImages, post);

      assertThat(outcome).isEqualTo(IndexOutcome.ADDED);
      IndexedPost indexed = stored(post);
      assertThat(indexed.chunks()).hasSize(1);
      assertThat(indexed.images())
          .extracting(
              IndexedImage::index,
              IndexedImage::url,
              IndexedImage::description,
              IndexedImage::embedding)
          .containsExactly(
              tuple(0, URI.create(A_JPG), "Swan", swanVector),
              tuple(1, URI.create(B_PNG), "Grapes", grapesVector));
      assertThat(embedder.imageCalls()).containsExactly(pixels(1), pixels(2));
      assertThat(embedder.passageCalls())
          .singleElement()
          .satisfies(
              passages ->
                  assertThat(passages)
                      .extracting(Passage::text)
                      .singleElement()
                      .asString()
                      .startsWith("Swans\n"));
    }

    @Test
    @DisplayName("AC-SRCH-69: images are fetched for the post's canonical URL, in catalog order")
    void fetches_each_image_with_the_post_url() {
      images.fetching(A_JPG, pixels(1)).fetching(B_PNG, pixels(2));

      withImages.index(swans().images(A, B).build());

      assertThat(images.calls())
          .containsExactly(
              new InMemoryImageSource.Call(URI.create(A_JPG), URI.create(POST_URL)),
              new InMemoryImageSource.Call(URI.create(B_PNG), URI.create(POST_URL)));
    }

    @Test
    @DisplayName("AC-SRCH-69: the recipe is the text-only recipe followed by /images-v1")
    void the_recipe_has_the_image_part() {
      assertThat(withImages.recipe())
          .isEqualTo(new IndexRecipe(textOnly.recipe().id() + "/images-v1"));
    }

    @Test
    @DisplayName("AC-SRCH-73: a post with blank title and body is recorded with its image")
    void a_post_without_text_keeps_its_image() {
      images.fetching(A_JPG, pixels(1));

      IndexedPost indexed = withImages.index(swans().title(" ").body("").images(A).build());

      assertThat(indexed.chunks()).isEmpty();
      assertThat(indexed.images()).extracting(IndexedImage::url).containsExactly(URI.create(A_JPG));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nz.sounie.blogmcp.search.domain.index.PostIndexerImagesTest#imageRevisions")
    @DisplayName("AC-SRCH-70: an image change re-embeds; the same revision again is UNCHANGED")
    void an_image_change_re_embeds(String change, PostImage[] revisedImages) {
      images.fetching(A_JPG, pixels(1)).fetching(B_PNG, pixels(2));
      upsert(withImages, swans().images(A).build());
      PostToIndex revision = swans().images(revisedImages).build();

      assertThat(upsert(withImages, revision)).isEqualTo(IndexOutcome.RE_EMBEDDED);
      assertThat(stored(revision).images())
          .extracting(IndexedImage::url, IndexedImage::description)
          .containsExactlyElementsOf(
              Stream.of(revisedImages).map(i -> tuple(i.url(), i.description())).toList());
      assertThat(upsert(withImages, revision)).isEqualTo(IndexOutcome.UNCHANGED);
    }

    @Test
    @DisplayName("AC-SRCH-71: a rejected image is skipped, and not fetched again")
    void a_rejected_image_is_skipped_for_good() {
      images.fetching(A_JPG, pixels(1)).rejecting(PIXEL_GIF, ImageRejection.TOO_SMALL);
      PostToIndex post = swans().images(A, PostImage.of(PIXEL_GIF, "", "")).build();

      assertThat(upsert(withImages, post)).isEqualTo(IndexOutcome.ADDED);
      assertThat(stored(post).images())
          .extracting(IndexedImage::index, IndexedImage::url)
          .containsExactly(tuple(0, URI.create(A_JPG)));

      images.clearCalls();
      assertThat(upsert(withImages, post)).isEqualTo(IndexOutcome.UNCHANGED);
      assertThat(images.calls()).isEmpty();
    }

    @Test
    @DisplayName("AC-SRCH-69: a skipped image leaves a gap in the image indexes")
    void a_skipped_image_leaves_a_gap() {
      images.rejecting(PIXEL_GIF, ImageRejection.UNSUPPORTED_TYPE).fetching(A_JPG, pixels(1));

      IndexedPost indexed =
          withImages.index(swans().images(PostImage.of(PIXEL_GIF, "", ""), A).build());

      assertThat(indexed.images()).extracting(IndexedImage::index).containsExactly(1);
    }

    @Test
    @DisplayName("AC-SRCH-72: an unavailable image is retried until it is settled")
    void an_unavailable_image_is_retried_until_settled() {
      images.fetching(A_JPG, pixels(1)).unavailable(B_PNG);
      PostToIndex post = swans().images(A, B).build();

      assertThat(upsert(withImages, post)).isEqualTo(IndexOutcome.ADDED);
      assertThat(stored(post).images()).extracting(IndexedImage::index).containsExactly(0);

      assertThat(upsert(withImages, post)).isEqualTo(IndexOutcome.RE_EMBEDDED);
      assertThat(stored(post).images()).extracting(IndexedImage::index).containsExactly(0);

      images.fetching(B_PNG, pixels(2));
      assertThat(upsert(withImages, post)).isEqualTo(IndexOutcome.RE_EMBEDDED);
      assertThat(stored(post).images()).extracting(IndexedImage::index).containsExactly(0, 1);

      assertThat(upsert(withImages, post)).isEqualTo(IndexOutcome.UNCHANGED);
    }

    @Test
    @DisplayName("AC-SRCH-72: an image whose embedding fails is retried until it is settled")
    void an_image_whose_embedding_fails_is_retried() {
      images.fetching(A_JPG, pixels(1)).fetching(B_PNG, pixels(2));
      embedder.failingForImage(pixels(2));
      PostToIndex post = swans().images(A, B).build();

      assertThat(upsert(withImages, post)).isEqualTo(IndexOutcome.ADDED);
      assertThat(stored(post).images()).extracting(IndexedImage::index).containsExactly(0);

      embedder.imagesWorking();
      assertThat(upsert(withImages, post)).isEqualTo(IndexOutcome.RE_EMBEDDED);
      assertThat(stored(post).images()).extracting(IndexedImage::index).containsExactly(0, 1);
      assertThat(upsert(withImages, post)).isEqualTo(IndexOutcome.UNCHANGED);
    }

    @Test
    @DisplayName("AC-SRCH-72, AC-SRCH-17: a passage-embedder failure fails the whole post")
    void a_passage_failure_keeps_the_old_entry() {
      images.fetching(A_JPG, pixels(1));
      PostToIndex post = swans().images(A).build();
      upsert(withImages, post);
      IndexedPost before = stored(post);
      embedder.failing();

      assertThatThrownBy(() -> upsert(withImages, swans().body("A new body").images(A).build()))
          .isInstanceOf(EmbedderUnavailable.class);
      assertThat(index.find(post.id())).containsSame(before);
    }
  }
}
