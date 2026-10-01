package nz.sounie.blogmcp.search.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IndexRecipeTest {

  private static final String MODEL = "bge-small-en-v1.5-q";
  private static final ChunkingPolicy CHUNKING = new ChunkingPolicy(300, 400, 50, 100);
  private static final PassageComposition COMPOSITION = new PassageComposition(64, 1);

  private static IndexRecipe standard() {
    return IndexRecipe.of(MODEL, CHUNKING, COMPOSITION);
  }

  @Test
  void same_parts_give_the_same_recipe() {
    assertThat(IndexRecipe.of(MODEL, ChunkingPolicy.standard(), PassageComposition.standard()))
        .isEqualTo(standard());
  }

  @Test
  void starts_with_the_model_id() {
    assertThat(standard().id()).startsWith(MODEL + "/");
  }

  static Stream<Arguments> changedParts() {
    return Stream.of(
        Arguments.of("model", IndexRecipe.of("other-model", CHUNKING, COMPOSITION)),
        Arguments.of(
            "target words",
            IndexRecipe.of(MODEL, new ChunkingPolicy(250, 400, 50, 100), COMPOSITION)),
        Arguments.of(
            "body tokens",
            IndexRecipe.of(MODEL, new ChunkingPolicy(300, 380, 50, 100), COMPOSITION)),
        Arguments.of(
            "overlap words",
            IndexRecipe.of(MODEL, new ChunkingPolicy(300, 400, 40, 100), COMPOSITION)),
        Arguments.of(
            "overlap token cap",
            IndexRecipe.of(MODEL, new ChunkingPolicy(300, 400, 50, 90), COMPOSITION)),
        Arguments.of(
            "title tokens", IndexRecipe.of(MODEL, CHUNKING, new PassageComposition(48, 1))),
        Arguments.of(
            "composition version", IndexRecipe.of(MODEL, CHUNKING, new PassageComposition(64, 2))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("changedParts")
  @DisplayName("AC-SRCH-29: changing any part of the recipe changes its identity")
  void changing_any_part_changes_the_recipe(String part, IndexRecipe changed) {
    assertThat(changed).isNotEqualTo(standard());
  }

  @Test
  @DisplayName("AC-SRCH-32: the query instruction is not part of the recipe")
  void query_instruction_is_not_part_of_the_recipe() {
    assertThat(standard().id()).doesNotContain(QueryPassage.INSTRUCTION.strip());
  }
}
