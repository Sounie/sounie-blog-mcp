package nz.sounie.blogmcp.search.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The real BGE-small-en-v1.5 (quantised) model through LangChain4j; no test doubles. */
@Tag("model")
class OnnxEmbedderTest {

  private static final String QUERY = "how do I configure a Gradle build for Java";
  private static final Passage RELATED =
      new Passage(
          "Writing build.gradle.kts for a Java project\nApply the java plugin, set the toolchain"
              + " to Java 25, add mavenCentral() as a repository and declare JUnit as a test"
              + " dependency.");
  private static final Passage UNRELATED =
      new Passage(
          "Baking sourdough bread\nFeed the starter, mix flour, water and salt, let the dough"
              + " prove overnight, then bake in a hot Dutch oven.");

  private final OnnxEmbedder embedder = RealModel.embedder();

  private static QueryPassage query(String text) {
    return QueryPassage.of(new QueryText(text));
  }

  @Test
  void model_id_is_bge_small_quantised() {
    assertThat(embedder.modelId()).isEqualTo("bge-small-en-v1.5-q");
  }

  @Test
  @DisplayName("AC-SRCH-10: the related passage is more similar to the query than the unrelated")
  void ranks_related_text_above_unrelated() {
    Embedding q = embedder.embedQuery(query(QUERY));
    List<Embedding> passages = embedder.embedPassages(List.of(RELATED, UNRELATED));

    assertUnitAnd384(q);
    passages.forEach(OnnxEmbedderTest::assertUnitAnd384);
    assertThat(q.similarityTo(passages.get(0)).value())
        .isGreaterThan(q.similarityTo(passages.get(1)).value());
  }

  @Test
  void the_same_input_gives_the_same_vector() {
    assertSameVector(
        embedder.embedPassages(List.of(RELATED)).getFirst(),
        embedder.embedPassages(List.of(RELATED)).getFirst());
    assertSameVector(embedder.embedQuery(query(QUERY)), embedder.embedQuery(query(QUERY)));
  }

  @Test
  void passages_are_embedded_in_order_one_each() {
    List<Embedding> both = embedder.embedPassages(List.of(RELATED, UNRELATED));

    assertThat(both).hasSize(2);
    assertSameVector(both.get(0), embedder.embedPassages(List.of(RELATED)).getFirst());
    assertSameVector(both.get(1), embedder.embedPassages(List.of(UNRELATED)).getFirst());
  }

  @Test
  @DisplayName("AC-SRCH-32: the query path embeds exactly the query passage text, nothing added")
  void query_path_embeds_the_query_passage_text() {
    Embedding viaQuery = embedder.embedQuery(query(QUERY));
    Embedding sameTextAsPassage =
        embedder.embedPassages(List.of(new Passage(QueryPassage.INSTRUCTION + QUERY))).getFirst();
    Embedding withoutInstruction = embedder.embedPassages(List.of(new Passage(QUERY))).getFirst();

    assertSameVector(viaQuery, sameTextAsPassage);
    assertThat(viaQuery.similarityTo(withoutInstruction).value()).isLessThan(0.9999);
  }

  @Test
  void no_passages_give_no_embeddings() {
    assertThat(embedder.embedPassages(List.of())).isEmpty();
  }

  @Test
  void concurrent_calls_give_the_same_results_as_sequential_ones() throws Exception {
    Embedding expectedQuery = embedder.embedQuery(query(QUERY));
    Embedding expectedPassage = embedder.embedPassages(List.of(RELATED)).getFirst();
    List<Callable<Embedding>> calls = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      calls.add(() -> embedder.embedQuery(query(QUERY)));
      calls.add(() -> embedder.embedPassages(List.of(RELATED)).getFirst());
    }

    List<Future<Embedding>> results;
    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      results = pool.invokeAll(calls, 2, TimeUnit.MINUTES);
    }

    for (int i = 0; i < results.size(); i++) {
      assertSameVector(results.get(i).get(), i % 2 == 0 ? expectedQuery : expectedPassage);
    }
  }

  private static void assertUnitAnd384(Embedding e) {
    assertThat(e.values()).hasSize(384);
    assertThat(e.similarityTo(e).value()).isCloseTo(1.0, within(1e-5));
  }

  private static void assertSameVector(Embedding actual, Embedding expected) {
    assertThat(actual.values()).containsExactly(expected.values(), within(1e-5f));
  }
}
