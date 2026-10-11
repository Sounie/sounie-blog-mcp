package nz.sounie.blogmcp.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** app.md 3.1: the single owner of tool input rules. */
class ToolArgumentsTest {

  private static final Set<String> NAMES = Set.of("query", "from", "limit");

  private static ToolArguments args(Map<String, Object> raw) {
    return ToolArguments.of(raw, NAMES);
  }

  @Nested
  class Names {

    @Test
    void an_argument_name_not_in_the_schema_is_rejected_by_name() {
      assertThatThrownBy(() -> args(Map.of("query", "java", "date_from", "2024-01-01")))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("date_from");
    }

    @Test
    void a_call_without_arguments_has_none() {
      ToolArguments none = ToolArguments.of(null, NAMES);

      assertThat(none.optionalString("from")).isEmpty();
      assertThatThrownBy(() -> none.requiredString("query"))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("query");
    }
  }

  @Nested
  class RequiredString {

    @Test
    void returns_the_string() {
      assertThat(args(Map.of("query", "records in java")).requiredString("query"))
          .isEqualTo("records in java");
    }

    @Test
    void a_missing_argument_is_rejected_by_name() {
      assertThatThrownBy(() -> args(Map.of()).requiredString("query"))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("query");
    }

    @Test
    void a_null_argument_counts_as_missing() {
      Map<String, Object> raw = new HashMap<>();
      raw.put("query", null);

      assertThatThrownBy(() -> args(raw).requiredString("query"))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("query");
    }

    @Test
    void a_non_string_is_rejected_by_name() {
      assertThatThrownBy(() -> args(Map.of("query", 42)).requiredString("query"))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("query");
    }
  }

  @Nested
  class OptionalString {

    @Test
    void returns_the_string_when_present() {
      assertThat(args(Map.of("query", "x")).optionalString("query")).contains("x");
    }

    @Test
    void is_empty_when_absent() {
      assertThat(args(Map.of()).optionalString("query")).isEmpty();
    }

    @Test
    void a_non_string_is_rejected_by_name() {
      assertThatThrownBy(() -> args(Map.of("query", true)).optionalString("query"))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("query");
    }
  }

  @Nested
  class OptionalDate {

    @Test
    void parses_an_iso_date() {
      assertThat(args(Map.of("from", "2024-04-01")).optionalDate("from"))
          .contains(LocalDate.of(2024, 4, 1));
    }

    @Test
    void is_empty_when_absent() {
      assertThat(args(Map.of()).optionalDate("from")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"01/04/2024", "2024-4-1", "2024-02-30", "yesterday", ""})
    void text_that_is_not_a_yyyy_MM_dd_date_is_rejected_naming_argument_and_value(String text) {
      assertThatThrownBy(() -> args(Map.of("from", text)).optionalDate("from"))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("from")
          .hasMessageContaining("yyyy-MM-dd")
          .hasMessageContaining(text);
    }

    @Test
    void a_non_string_is_rejected_by_name() {
      assertThatThrownBy(() -> args(Map.of("from", 20240401)).optionalDate("from"))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("from");
    }
  }

  @Nested
  class OptionalInteger {

    @Test
    void accepts_an_integer() {
      assertThat(args(Map.of("limit", 5)).optionalInteger("limit")).contains(5);
    }

    @Test
    void accepts_a_long() {
      assertThat(args(Map.of("limit", 5L)).optionalInteger("limit")).contains(5);
    }

    @Test
    void accepts_an_integral_double() {
      assertThat(args(Map.of("limit", 5.0)).optionalInteger("limit")).contains(5);
    }

    @Test
    void is_empty_when_absent() {
      assertThat(args(Map.of()).optionalInteger("limit")).isEmpty();
    }

    @Test
    void a_fractional_double_is_rejected_by_name() {
      assertThatThrownBy(() -> args(Map.of("limit", 5.5)).optionalInteger("limit"))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("limit");
    }

    @Test
    void a_string_is_rejected_by_name() {
      assertThatThrownBy(() -> args(Map.of("limit", "ten")).optionalInteger("limit"))
          .isInstanceOf(InvalidToolArgument.class)
          .hasMessageContaining("limit");
    }
  }
}
