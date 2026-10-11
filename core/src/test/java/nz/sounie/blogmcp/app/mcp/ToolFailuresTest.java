package nz.sounie.blogmcp.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import nz.sounie.blogmcp.catalog.domain.post.InvalidPostReference;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.query.InvalidSearchQuery;
import org.junit.jupiter.api.Test;

/** app.md 3.1: the rule table from exception type to a Failed message; first match wins. */
class ToolFailuresTest {

  private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
  private final ToolFailures failures =
      new ToolFailures(new PrintStream(stderr, true, StandardCharsets.UTF_8));

  private String stderr() {
    return stderr.toString(StandardCharsets.UTF_8);
  }

  private ToolOutcome failingWith(RuntimeException e) {
    return failures.guard(
        () -> {
          throw e;
        });
  }

  @Test
  void a_successful_call_passes_its_outcome_through() {
    ToolOutcome answer = new ToolOutcome.NotFound("No post x:1");

    assertThat(failures.guard(() -> answer)).isSameAs(answer);
    assertThat(stderr()).isEmpty();
  }

  @Test
  void an_invalid_tool_argument_gives_its_own_message() {
    assertThat(failingWith(new InvalidToolArgument("`limit` must be a whole number, got ten")))
        .isEqualTo(new ToolOutcome.Failed("`limit` must be a whole number, got ten"));
  }

  @Test
  void an_invalid_search_query_gives_its_own_message() {
    assertThat(
            failingWith(
                new InvalidSearchQuery(InvalidSearchQuery.Reason.BLANK, "The query is blank")))
        .isEqualTo(new ToolOutcome.Failed("The query is blank"));
  }

  @Test
  void an_invalid_post_reference_gives_its_own_message() {
    assertThat(failingWith(new InvalidPostReference("Not a post ID: 'not-an-id'")))
        .isEqualTo(new ToolOutcome.Failed("Not a post ID: 'not-an-id'"));
  }

  @Test
  void an_unavailable_embedder_says_search_is_temporarily_unavailable_and_logs_one_line() {
    ToolOutcome outcome = failingWith(new EmbedderUnavailable("ONNX session failed"));

    assertThat(outcome)
        .isEqualTo(
            new ToolOutcome.Failed(
                "search is temporarily unavailable because the local model failed"));
    assertThat(stderr().lines()).hasSize(1);
  }

  @Test
  void any_other_failure_gives_a_generic_message_and_its_stack_trace_goes_to_stderr() {
    ToolOutcome outcome = failingWith(new IllegalStateException("secret internal detail"));

    assertThat(outcome)
        .isInstanceOfSatisfying(
            ToolOutcome.Failed.class,
            failed ->
                assertThat(failed.message())
                    .isNotBlank()
                    .doesNotContain("secret internal detail")
                    .doesNotContain("IllegalStateException"));
    assertThat(stderr())
        .contains("IllegalStateException")
        .contains("secret internal detail")
        .contains("\tat ");
  }
}
