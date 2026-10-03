package nz.sounie.blogmcp.app.mcp;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The raw arguments of one tool call, read field by field with type checks. The only owner of input
 * validation: the SDK's schema validation is switched off (app.md 3.1).
 */
public final class ToolArguments {

  private ToolArguments() {}

  /**
   * @param raw the call's arguments as decoded by the SDK; {@code null} when the call had none
   * @param names the argument names in the tool's input schema
   * @throws InvalidToolArgument naming any argument that is not in {@code names}
   */
  public static ToolArguments of(Map<String, Object> raw, Set<String> names) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }

  /**
   * @throws InvalidToolArgument if the argument is missing or not a string
   */
  public String requiredString(String name) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }

  /**
   * @throws InvalidToolArgument if the argument is present but not a string
   */
  public Optional<String> optionalString(String name) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }

  /**
   * @throws InvalidToolArgument if the argument is present but not a {@code yyyy-MM-dd} string
   */
  public Optional<LocalDate> optionalDate(String name) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }

  /**
   * Accepts {@code Integer} and {@code Long}, and a {@code Double} only if it is integral.
   *
   * @throws InvalidToolArgument if the argument is present but not a whole number
   */
  public Optional<Integer> optionalInteger(String name) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.1)");
  }
}
