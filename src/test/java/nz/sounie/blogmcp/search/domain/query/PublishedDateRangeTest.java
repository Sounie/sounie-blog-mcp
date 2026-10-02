package nz.sounie.blogmcp.search.domain.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Executable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.text.InvalidSearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublishedDateRangeTest {

  // NZ daylight saving ended on 2024-04-07: 1 April is NZDT (UTC+13), 30 April is NZST (UTC+12).
  private static final Instant P1 = Instant.parse("2024-03-31T10:59:59Z"); // 31 Mar 23:59:59 NZDT
  private static final Instant P2 = Instant.parse("2024-03-31T11:30:00Z"); // 1 Apr 00:30 NZDT
  private static final Instant P3 = Instant.parse("2024-04-30T11:59:59.999Z"); // 30 Apr NZST
  private static final Instant P4 = Instant.parse("2024-04-30T12:00:00Z"); // 1 May 00:00 NZST

  private static final LocalDate MAR_31 = LocalDate.of(2024, 3, 31);
  private static final LocalDate APR_1 = LocalDate.of(2024, 4, 1);
  private static final LocalDate APR_30 = LocalDate.of(2024, 4, 30);

  @Test
  @DisplayName("AC-SRCH-21: from after to is rejected as FROM_AFTER_TO")
  void rejects_from_after_to() {
    assertThatThrownBy(() -> PublishedDateRange.between(LocalDate.of(2024, 5, 1), APR_30))
        .isInstanceOfSatisfying(
            InvalidSearchQuery.class,
            e -> assertThat(e.reason()).isEqualTo(InvalidSearchQuery.Reason.FROM_AFTER_TO));
  }

  @Test
  @DisplayName("AC-SRCH-27: to = 2024-03-31 includes only P1 (NZDT boundary)")
  void to_end_of_march_includes_only_p1() {
    assertIncludes(PublishedDateRange.to(MAR_31), true, false, false, false);
  }

  @Test
  @DisplayName("AC-SRCH-27: from = 2024-04-01 includes P2 although its UTC date is 31 March")
  void from_april_first_includes_p2_p3_p4() {
    assertIncludes(PublishedDateRange.from(APR_1), false, true, true, true);
  }

  @Test
  @DisplayName("AC-SRCH-27: 2024-04-01..2024-04-30 includes P2 and P3 (NZST boundary)")
  void april_includes_p2_and_p3() {
    assertIncludes(PublishedDateRange.between(APR_1, APR_30), false, true, true, false);
  }

  @Test
  @DisplayName("AC-SRCH-21, AC-SRCH-27: from = to is a single day")
  void single_day_includes_only_p2() {
    assertIncludes(PublishedDateRange.between(APR_1, APR_1), false, true, false, false);
  }

  @Test
  @DisplayName("AC-SRCH-21: an unbounded range includes everything")
  void unbounded_includes_everything() {
    assertIncludes(PublishedDateRange.unbounded(), true, true, true, true);
  }

  @Test
  @DisplayName("AC-SRCH-27: the zone is the constant Pacific/Auckland")
  void zone_is_pacific_auckland() {
    assertThat(PublishedDateRange.ZONE).isEqualTo(ZoneId.of("Pacific/Auckland"));
  }

  @Test
  @DisplayName("AC-SRCH-27: no factory or method takes a zone argument")
  void takes_no_zone_argument() {
    Stream<Executable> publicApi =
        Stream.concat(
            Arrays.stream(PublishedDateRange.class.getMethods()),
            Arrays.stream(PublishedDateRange.class.getConstructors()));

    assertThat(publicApi.flatMap(m -> Arrays.stream(m.getParameterTypes())))
        .doesNotContain(ZoneId.class);
  }

  private static void assertIncludes(
      PublishedDateRange range, boolean p1, boolean p2, boolean p3, boolean p4) {
    assertThat(
            new boolean[] {
              range.includes(P1), range.includes(P2), range.includes(P3), range.includes(P4)
            })
        .containsExactly(p1, p2, p3, p4);
  }
}
