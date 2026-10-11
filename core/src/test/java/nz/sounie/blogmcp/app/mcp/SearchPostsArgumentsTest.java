package nz.sounie.blogmcp.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import nz.sounie.blogmcp.search.domain.query.InvalidSearchQuery;
import nz.sounie.blogmcp.search.domain.query.PublishedDateRange;
import nz.sounie.blogmcp.search.domain.query.QueryText;
import nz.sounie.blogmcp.search.domain.query.ResultLimit;
import nz.sounie.blogmcp.search.domain.query.SearchQuery;
import nz.sounie.blogmcp.search.domain.query.SiteFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** app.md 3.1: search_posts arguments to a SearchQuery, without if-chains. */
class SearchPostsArgumentsTest {

  private final SiteChoices sites = SiteChoices.of(List.of("sounie-wp", "elegant"));

  private SearchQuery queryFor(Map<String, Object> raw) {
    return SearchPostsArguments.read(raw).toQuery(sites);
  }

  /** The first instant of a calendar day in the blog time zone. */
  private static Instant startOf(LocalDate day) {
    return day.atStartOfDay(PublishedDateRange.ZONE).toInstant();
  }

  private static Instant endOf(LocalDate day) {
    return day.atTime(LocalTime.MAX).atZone(PublishedDateRange.ZONE).toInstant();
  }

  @Test
  @DisplayName("AC-APP-2: every argument reaches the query")
  void maps_every_argument() {
    SearchQuery query =
        queryFor(
            Map.of(
                "query", "records in java",
                "site", "elegant",
                "from", "2024-04-01",
                "to", "2024-12-31",
                "limit", 5));

    assertThat(query.text()).isEqualTo(new QueryText("records in java"));
    assertThat(query.site()).isEqualTo(new SiteFilter.OnlySite(new SiteId("elegant")));
    assertThat(query.limit()).isEqualTo(ResultLimit.of(5));
    PublishedDateRange dates = query.dates();
    assertThat(dates.includes(startOf(LocalDate.of(2024, 4, 1)))).isTrue();
    assertThat(dates.includes(endOf(LocalDate.of(2024, 3, 31)))).isFalse();
    assertThat(dates.includes(endOf(LocalDate.of(2024, 12, 31)))).isTrue();
    assertThat(dates.includes(startOf(LocalDate.of(2025, 1, 1)))).isFalse();
  }

  @Test
  @DisplayName("AC-APP-2: only a query means any site, any date and the default limit")
  void defaults_everything_but_the_query() {
    SearchQuery query = queryFor(Map.of("query", "gradle"));

    assertThat(query.site()).isEqualTo(new SiteFilter.AnySite());
    assertThat(query.limit()).isEqualTo(ResultLimit.defaultLimit());
    assertThat(query.dates().includes(Instant.parse("1970-01-01T00:00:00Z"))).isTrue();
    assertThat(query.dates().includes(Instant.parse("2999-12-31T00:00:00Z"))).isTrue();
  }

  @Test
  void only_from_bounds_the_start() {
    PublishedDateRange dates = queryFor(Map.of("query", "x", "from", "2024-04-01")).dates();

    assertThat(dates.includes(endOf(LocalDate.of(2024, 3, 31)))).isFalse();
    assertThat(dates.includes(Instant.parse("2999-12-31T00:00:00Z"))).isTrue();
  }

  @Test
  void only_to_bounds_the_end() {
    PublishedDateRange dates = queryFor(Map.of("query", "x", "to", "2024-12-31")).dates();

    assertThat(dates.includes(Instant.parse("1970-01-01T00:00:00Z"))).isTrue();
    assertThat(dates.includes(startOf(LocalDate.of(2025, 1, 1)))).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"50, 20", "0, 1", "-3, 1", "20, 20", "1, 1"})
  @DisplayName("AC-APP-2: a limit outside 1..20 is clamped, not rejected")
  void clamps_the_limit(int requested, int expected) {
    assertThat(queryFor(Map.of("query", "x", "limit", requested)).limit().value())
        .isEqualTo(expected);
  }

  @Test
  @DisplayName("AC-APP-3: from after to is still the domain's rejection")
  void from_after_to_is_rejected_by_the_domain() {
    assertThatThrownBy(
            () -> queryFor(Map.of("query", "x", "from", "2024-12-31", "to", "2024-04-01")))
        .isInstanceOf(InvalidSearchQuery.class);
  }

  @Test
  @DisplayName("AC-APP-3: a blank query is still the domain's rejection")
  void a_blank_query_is_rejected_by_the_domain() {
    assertThatThrownBy(() -> queryFor(Map.of("query", "   ")))
        .isInstanceOf(InvalidSearchQuery.class);
  }

  @Test
  @DisplayName("AC-APP-3: an argument outside the schema is rejected")
  void an_unknown_argument_is_rejected() {
    assertThatThrownBy(() -> SearchPostsArguments.read(Map.of("query", "x", "date_from", "2024")))
        .isInstanceOf(InvalidToolArgument.class)
        .hasMessageContaining("date_from");
  }

  @Test
  @DisplayName("AC-APP-3: an unknown site is rejected")
  void an_unknown_site_is_rejected() {
    assertThatThrownBy(() -> queryFor(Map.of("query", "x", "site", "nope")))
        .isInstanceOf(InvalidToolArgument.class)
        .hasMessageContaining("nope");
  }
}
