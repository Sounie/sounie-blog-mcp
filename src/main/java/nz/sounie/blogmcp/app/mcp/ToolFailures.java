package nz.sounie.blogmcp.app.mcp;

import java.io.PrintStream;
import java.util.function.Supplier;

/**
 * The only {@code catch} in the tool path: a rule table from exception type to a {@link
 * ToolOutcome.Failed} message (first match wins), so a tool can never crash the server.
 */
public final class ToolFailures {

  public ToolFailures(PrintStream errors) {
    // red: the implementer keeps the stream for diagnostics
  }

  /** The call's outcome, or the failure it was turned into. Never throws a RuntimeException. */
  public ToolOutcome guard(Supplier<ToolOutcome> call) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }
}
