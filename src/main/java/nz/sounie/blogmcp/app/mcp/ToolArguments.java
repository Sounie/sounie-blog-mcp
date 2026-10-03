package nz.sounie.blogmcp.app.mcp;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The raw arguments of one tool call, read field by field with type checks. The only owner of input
 * validation: the SDK's schema validation is switched off (app.md 3.1).
 */
public final class ToolArguments {

  private final Map<String, Object> raw;

  private ToolArguments(Map<String, Object> raw) {
    this.raw = raw;
  }

  /**
   * @param raw the call's arguments as decoded by the SDK; {@code null} when the call had none
   * @param names the argument names in the tool's input schema
   * @throws InvalidToolArgument naming any argument that is not in {@code names}
   */
  public static ToolArguments of(Map<String, Object> raw, Set<String> names) {
    Map<String, Object> arguments = Optional.ofNullable(raw).orElseGet(Map::of);
    arguments.keySet().stream()
        .filter(name -> !names.contains(name))
        .findFirst()
        .ifPresent(
            unknown -> {
              throw new InvalidToolArgument(
                  "Unknown argument `" + unknown + "`; expected one of " + new TreeSet<>(names));
            });
    // A copy that tolerates null values, which the SDK may decode from JSON null.
    return new ToolArguments(Collections.unmodifiableMap(new HashMap<>(arguments)));
  }

  /**
   * @throws InvalidToolArgument if the argument is missing or not a string
   */
  public String requiredString(String name) {
    return optionalString(name)
        .orElseThrow(() -> new InvalidToolArgument("`" + name + "` is required: a string"));
  }

  /**
   * @throws InvalidToolArgument if the argument is present but not a string
   */
  public Optional<String> optionalString(String name) {
    return present(name).map(value -> asString(name, value));
  }

  /**
   * @throws InvalidToolArgument if the argument is present but not a {@code yyyy-MM-dd} string
   */
  public Optional<LocalDate> optionalDate(String name) {
    return optionalString(name).map(text -> asDate(name, text));
  }

  /**
   * Accepts {@code Integer} and {@code Long}, and a {@code Double} only if it is integral.
   *
   * @throws InvalidToolArgument if the argument is present but not a whole number
   */
  public Optional<Integer> optionalInteger(String name) {
    return present(name).map(value -> asWholeNumber(name, value));
  }

  private Optional<Object> present(String name) {
    return Optional.ofNullable(raw.get(name));
  }

  private static String asString(String name, Object value) {
    return switch (value) {
      case String text -> text;
      default -> throw new InvalidToolArgument("`" + name + "` must be a string, got " + value);
    };
  }

  private static LocalDate asDate(String name, String text) {
    try {
      // ISO_LOCAL_DATE resolves strictly: two-digit month and day, and a real calendar date.
      return LocalDate.parse(text);
    } catch (DateTimeParseException e) {
      throw new InvalidToolArgument("`" + name + "` must be a date yyyy-MM-dd, got `" + text + "`");
    }
  }

  /** A whole number outside the {@code int} range saturates; callers clamp anyway. */
  private static int asWholeNumber(String name, Object value) {
    return switch (value) {
      case Number number when isWhole(number) -> (int) number.doubleValue();
      default ->
          throw new InvalidToolArgument("`" + name + "` must be a whole number, got " + value);
    };
  }

  private static boolean isWhole(Number number) {
    double value = number.doubleValue();
    return Double.isFinite(value) && value == Math.rint(value);
  }
}
