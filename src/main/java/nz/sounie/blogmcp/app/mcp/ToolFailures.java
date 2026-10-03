package nz.sounie.blogmcp.app.mcp;

import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import nz.sounie.blogmcp.catalog.domain.post.InvalidPostReference;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.query.InvalidSearchQuery;

/**
 * The only {@code catch} in the tool path: a rule table from exception type to a {@link
 * ToolOutcome.Failed} message (first match wins), so a tool can never crash the server.
 */
public final class ToolFailures {

  static final String MODEL_FAILED =
      "search is temporarily unavailable because the local model failed";
  static final String UNEXPECTED =
      "The tool failed unexpectedly. The details are in the server log; please try again.";

  private final PrintStream errors;
  private final List<Rule> rules;

  public ToolFailures(PrintStream errors) {
    this.errors = Objects.requireNonNull(errors, "errors");
    this.rules =
        List.of(
            new Rule(InvalidToolArgument.class, ToolFailures::ownMessage),
            new Rule(InvalidSearchQuery.class, ToolFailures::ownMessage),
            new Rule(InvalidPostReference.class, ToolFailures::ownMessage),
            new Rule(EmbedderUnavailable.class, this::modelFailed));
  }

  /** The call's outcome, or the failure it was turned into. Never throws a RuntimeException. */
  public ToolOutcome guard(Supplier<ToolOutcome> call) {
    try {
      return call.get();
    } catch (RuntimeException e) {
      return failureFor(e);
    }
  }

  private ToolOutcome failureFor(RuntimeException e) {
    return rules.stream()
        .flatMap(rule -> rule.apply(e).stream())
        .findFirst()
        .orElseGet(() -> unexpected(e));
  }

  private static ToolOutcome ownMessage(RuntimeException e) {
    return new ToolOutcome.Failed(e.getMessage());
  }

  private ToolOutcome modelFailed(RuntimeException e) {
    errors.println("search: the local model failed: " + e.getMessage());
    return new ToolOutcome.Failed(MODEL_FAILED);
  }

  private ToolOutcome unexpected(RuntimeException e) {
    errors.println("blog-mcp: a tool call failed unexpectedly");
    e.printStackTrace(errors);
    return new ToolOutcome.Failed(UNEXPECTED);
  }

  /** One row of the table: an exception type and the failure it becomes. */
  private record Rule(
      Class<? extends RuntimeException> type, Function<RuntimeException, ToolOutcome> outcome) {

    Optional<ToolOutcome> apply(RuntimeException e) {
      return Optional.of(e).filter(type::isInstance).map(outcome);
    }
  }
}
