package nz.sounie.blogmcp.search.adapter.out.embedding.gemma2.server;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.query.QueryPassage;
import nz.sounie.blogmcp.search.domain.text.Passage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The OpenAI-compatible protocol over real HTTP (WireMock standing in for LM Studio), through the
 * real LangChain4j client. Runs in CI; the real model is covered by {@code Gemma2EmbedderTest}.
 */
class Gemma2EmbedderHttpTest {

  private static final String EMBEDDINGS = "/v1/embeddings";
  private static final Duration TIMEOUT = Duration.ofMillis(500);

  @RegisterExtension
  static final WireMockExtension lmStudio =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private Gemma2Embedder embedder() {
    return new Gemma2Embedder(lmStudio.baseUrl() + "/v1", TIMEOUT);
  }

  private static String embeddings(int count, int dimension) {
    String vector =
        IntStream.range(0, dimension)
            .mapToObj(i -> i == 0 ? "1.0" : "0.0")
            .collect(Collectors.joining(",", "[", "]"));
    String data =
        IntStream.range(0, count)
            .mapToObj(
                i -> "{\"object\":\"embedding\",\"index\":" + i + ",\"embedding\":" + vector + "}")
            .collect(Collectors.joining(","));
    return "{\"object\":\"list\",\"data\":["
        + data
        + "],\"model\":\"text-embedding-embeddinggemma-2\","
        + "\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
  }

  private void replies(String body) {
    lmStudio.stubFor(
        post(urlPathEqualTo(EMBEDDINGS))
            .willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(body)));
  }

  @Test
  void embeds_a_query_with_the_model_name_and_the_prepared_text() {
    replies(embeddings(1, 768));

    float[] vector =
        embedder()
            .embedQuery(new QueryPassage("task: search result | query: records in java"))
            .values();

    assertThat(vector).hasSize(768);
    assertThat(vector[0]).isEqualTo(1.0f);
    lmStudio.verify(
        postRequestedFor(urlPathEqualTo(EMBEDDINGS))
            .withRequestBody(matchingJsonPath("$.model", containing("embeddinggemma-2")))
            .withRequestBody(containing("task: search result | query: records in java")));
  }

  @Test
  void embeds_passages_in_order() {
    replies(embeddings(2, 768));

    assertThat(embedder().embedPassages(List.of(new Passage("first"), new Passage("second"))))
        .hasSize(2);
  }

  @Test
  void no_passages_need_no_request() {
    assertThat(embedder().embedPassages(List.of())).isEmpty();
    assertThat(lmStudio.getAllServeEvents()).isEmpty();
  }

  @Test
  @DisplayName("Review #5: a server error makes queries and indexing unavailable, not a crash")
  void a_server_error_makes_search_unavailable() {
    lmStudio.stubFor(post(urlPathEqualTo(EMBEDDINGS)).willReturn(aResponse().withStatus(500)));
    Gemma2Embedder embedder = embedder();

    assertThatThrownBy(() -> embedder.embedQuery(new QueryPassage("query")))
        .isInstanceOf(EmbedderUnavailable.class);
    assertThatThrownBy(() -> embedder.embedPassages(List.of(new Passage("passage"))))
        .isInstanceOf(EmbedderUnavailable.class);
  }

  @Test
  @DisplayName("Review #5: an unreachable server makes search unavailable")
  void an_unreachable_server_makes_search_unavailable() {
    Gemma2Embedder embedder = new Gemma2Embedder("http://localhost:1/v1", TIMEOUT);

    assertThatThrownBy(() -> embedder.embedQuery(new QueryPassage("query")))
        .isInstanceOf(EmbedderUnavailable.class);
  }

  @Test
  @DisplayName("Review #5: a reply with the wrong number of values makes search unavailable")
  void a_reply_of_the_wrong_length_makes_search_unavailable() {
    replies(embeddings(1, 3));

    assertThatThrownBy(() -> embedder().embedQuery(new QueryPassage("query")))
        .isInstanceOf(EmbedderUnavailable.class);
  }

  @Test
  @DisplayName("Review #11: a stalled server fails after the timeout, without retrying")
  void a_stalled_server_fails_after_the_timeout_without_retrying() {
    lmStudio.stubFor(
        post(urlPathEqualTo(EMBEDDINGS))
            .willReturn(aResponse().withFixedDelay(3_000).withBody(embeddings(1, 768))));
    Gemma2Embedder embedder = embedder();

    long start = System.nanoTime();
    assertThatThrownBy(() -> embedder.embedQuery(new QueryPassage("query")))
        .isInstanceOf(EmbedderUnavailable.class);

    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    lmStudio.verify(1, postRequestedFor(urlPathEqualTo(EMBEDDINGS)));
  }
}
