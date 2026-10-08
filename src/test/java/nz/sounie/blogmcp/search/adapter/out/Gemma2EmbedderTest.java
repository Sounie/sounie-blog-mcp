package nz.sounie.blogmcp.search.adapter.out;

import nz.sounie.blogmcp.search.domain.embedding.Embedding;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.text.Passage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.*;

/** The real Gemma2 model through LangChain4j; no test doubles. */
@Tag("model")
class Gemma2EmbedderTest {
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

    private static QueryPassage query(String text) {
        return QueryPassage.of(new QueryText(text));
    }

    @Test
    void model_id_is_gemma2() {
        Gemma2Embedder embedder = new Gemma2Embedder();
        assertEquals("text-embedding-embeddinggemma-2", embedder.modelId());
    }

    @Test
    void ranks_related_text_above_unrelated() {
        Gemma2Embedder embedder = new Gemma2Embedder();
        var q = embedder.embedQuery(new QueryPassage(QUERY));
        var passages = embedder.embedPassages(List.of(RELATED, UNRELATED));

        assertTrue(q.similarityTo(passages.get(0)).value() > q.similarityTo(passages.get(1)).value());
    }

    @Test
    void the_same_input_gives_the_same_vector() {
        Gemma2Embedder embedder = new Gemma2Embedder();
        assertSameVector(
                embedder.embedPassages(List.of(RELATED)).getFirst(),
                embedder.embedPassages(List.of(RELATED)).getFirst());
        assertSameVector(embedder.embedQuery(query(QUERY)), embedder.embedQuery(query(QUERY)));
    }

    private static void assertSameVector(Embedding actual, Embedding expected) {
        assertThat(actual.values()).containsExactly(expected.values(), within(1e-5f));
    }
}