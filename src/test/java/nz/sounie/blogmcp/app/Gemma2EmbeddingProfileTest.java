package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import nz.sounie.blogmcp.search.domain.text.PassageTokenBudget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Gemma2EmbeddingProfileTest {

  private static final PassageTokenBudget EXPLICIT_BUDGET =
      new PassageTokenBudget(73, 11, 9, 96, 120);

  @Test
  @DisplayName("AC-SRCH-47: a profile explicitly pins model, matching tokenizer assets and budget")
  void profile_retains_explicit_revision_assets_and_budget() {
    Gemma2EmbeddingProfile profile =
        new Gemma2EmbeddingProfile(
            "fixture-revision-7",
            "fixture-revision-7",
            Path.of("model-assets/fixture-revision-7/tokenizer"),
            EXPLICIT_BUDGET);

    assertThatCode(profile::validate).doesNotThrowAnyException();
    assertThat(profile.modelRevision()).isEqualTo("fixture-revision-7");
    assertThat(profile.tokenizerRevision()).isEqualTo(profile.modelRevision());
    assertThat(profile.tokenizerAssets())
        .isEqualTo(Path.of("model-assets/fixture-revision-7/tokenizer"));
    assertThat(profile.passageTokenBudget()).isEqualTo(EXPLICIT_BUDGET);
  }

  @Test
  @DisplayName("AC-SRCH-47: a tokenizer from a different revision is not accepted")
  void rejects_tokenizer_assets_from_a_different_model_revision() {
    assertThatThrownBy(
            () ->
                new Gemma2EmbeddingProfile(
                        "fixture-revision-7",
                        "fixture-revision-6",
                        Path.of("model-assets/fixture-revision-6/tokenizer"),
                        EXPLICIT_BUDGET)
                    .validate())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("AC-SRCH-47: the configured prepared-passage budget fits explicit context")
  void rejects_a_prepared_passage_budget_above_explicit_supported_context() {
    assertThatThrownBy(() -> new PassageTokenBudget(73, 11, 9, 121, 120).validate())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("AC-SRCH-47: no provider budget is inferred when profile values are missing")
  void requires_an_explicit_profile_and_budget() {
    Gemma2EmbeddingProfile completeProfile =
        new Gemma2EmbeddingProfile(
            "fixture-revision-7",
            "fixture-revision-7",
            Path.of("model-assets/fixture-revision-7/tokenizer"),
            EXPLICIT_BUDGET);
    assertThatCode(completeProfile::validate)
        .doesNotThrowAnyException();
    assertThatThrownBy(
            () ->
                new Gemma2EmbeddingProfile(
                        "fixture-revision-7",
                        "fixture-revision-7",
                        Path.of("model-assets/fixture-revision-7/tokenizer"),
                        null)
                    .validate())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
