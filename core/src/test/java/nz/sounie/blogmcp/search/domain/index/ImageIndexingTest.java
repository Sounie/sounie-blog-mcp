package nz.sounie.blogmcp.search.domain.index;

import static org.assertj.core.api.Assertions.assertThat;

import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.adapter.out.InMemoryImageSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The recipe part each strategy owns. What each strategy does with a post's images is tested
 * through {@link PostIndexer} in {@code PostIndexerImagesTest}.
 */
class ImageIndexingTest {

  private static final IndexRecipe RECIPE =
      new IndexRecipe("model/w300-t400-o50-oc100-c64/tt64-p1");

  @Test
  @DisplayName("AC-SRCH-68: text-only image indexing leaves the recipe unchanged")
  void text_only_leaves_the_recipe_unchanged() {
    assertThat(ImageIndexing.textOnly().appliedTo(RECIPE)).isEqualTo(RECIPE);
  }

  @Test
  @DisplayName("AC-SRCH-69: image indexing appends /images-v1 to the recipe")
  void with_images_appends_the_image_part() {
    ImageIndexing withImages =
        ImageIndexing.withImages(new InMemoryImageSource(), new FakeEmbedder());

    assertThat(withImages.appliedTo(RECIPE)).isEqualTo(new IndexRecipe(RECIPE.id() + "/images-v1"));
  }
}
