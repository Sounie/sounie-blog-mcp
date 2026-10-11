package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.onnx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import nz.sounie.blogmcp.search.application.embedding.spi.EmbeddingProviderProfile;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The real bundled q8 model on ONNX Runtime; no test doubles and no model server. */
@Tag("model")
class Gemma2OnnxEmbedderTest {

  private static final Gemma2OnnxEmbedder EMBEDDER = new Gemma2OnnxEmbedder();

  private static final Passage RELATED =
      passage(
          "Writing build.gradle.kts for a Java project",
          "Apply the java plugin, set the toolchain to Java 25, add mavenCentral() as a repository"
              + " and declare JUnit as a test dependency.");
  private static final Passage UNRELATED =
      passage(
          "Baking sourdough bread",
          "Feed the starter, mix flour, water and salt, let the dough prove overnight, then bake"
              + " in a hot Dutch oven.");

  private static Passage passage(String title, String body) {
    return new Passage(EMBEDDER.preparePassage(title, new Passage(body)));
  }

  private static QueryPassage query(String text) {
    return new QueryPassage(EMBEDDER.prepareQuery(new QueryText(text)));
  }

  @Test
  @DisplayName("AC-SRCH-51: the in-process profile pins the ONNX revision and its own index key")
  void profile_pins_the_onnx_revision_and_its_own_index_key() {
    EmbeddingProviderProfile profile = EMBEDDER.profile();

    assertThat(EMBEDDER.modelId()).contains("embeddinggemma-2").contains("daa72c51").contains("q8");
    assertThat(profile.modelRevision()).isEqualTo("daa72c51243991dfcaf9f9137d2c573d8f7790c0");
    assertThat(profile.tokenizerRevision()).isEqualTo("914f7f89142e33e77833254d9c9b90c3cef7303b");
    assertThat(profile.indexProfileKey()).isEqualTo("gemma2-onnx-q8-daa72c51-v1");
    assertThat(profile.dimension()).isEqualTo(768);
    assertThat(profile.contextTokens()).isEqualTo(8192);
  }

  @Test
  @DisplayName("AC-SRCH-46, 51: queries and passages use the EmbeddingGemma templates")
  void prepares_queries_and_passages_with_the_gemma2_templates() {
    assertThat(EMBEDDER.prepareQuery(new QueryText("records in java")))
        .isEqualTo("task: search result | query: records in java");
    assertThat(EMBEDDER.preparePassage("", new Passage("canonical passage")))
        .isEqualTo("title: none | text: canonical passage");
  }

  @Test
  @DisplayName("AC-SRCH-51: vectors have 768 values and unit length")
  void vectors_have_768_values_and_unit_length() {
    Embedding vector = EMBEDDER.embedQuery(query("gradle toolchains"));

    assertThat(vector.values()).hasSize(768);
    assertThat(norm(vector)).isCloseTo(1.0, within(1e-3));
  }

  @Test
  void ranks_related_text_above_unrelated() {
    Embedding q = EMBEDDER.embedQuery(query("how do I configure a Gradle build for Java"));
    List<Embedding> passages = EMBEDDER.embedPassages(List.of(RELATED, UNRELATED));

    assertThat(q.similarityTo(passages.get(0)).value())
        .isGreaterThan(q.similarityTo(passages.get(1)).value());
  }

  @Test
  void the_same_input_gives_the_same_vector() {
    assertThat(EMBEDDER.embedPassages(List.of(RELATED)).getFirst().values())
        .containsExactly(
            EMBEDDER.embedPassages(List.of(RELATED)).getFirst().values(), within(1e-5f));
  }

  @Test
  @DisplayName("AC-SRCH-51: a passage near the 3,000-token body budget embeds in one piece")
  void embeds_a_passage_near_the_body_budget() {
    String body =
        "Hexagonal architecture keeps the domain independent of frameworks; adapters translate"
            + " between the outside world and the application's ports. ";
    Passage longPassage = passage("", body.repeat(130));

    assertThat(EMBEDDER.profile().tokenizer().countTokens(longPassage.text())).isGreaterThan(2500);
    assertThat(EMBEDDER.embedPassages(List.of(longPassage)).getFirst().values()).hasSize(768);
  }

  @Test
  void no_passages_need_no_embeddings() {
    assertThat(EMBEDDER.embedPassages(List.of())).isEmpty();
  }

  private static double norm(Embedding vector) {
    double sum = 0;
    for (float value : vector.values()) {
      sum += value * value;
    }
    return Math.sqrt(sum);
  }
}
