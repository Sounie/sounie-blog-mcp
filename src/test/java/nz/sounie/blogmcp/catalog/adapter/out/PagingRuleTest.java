package nz.sounie.blogmcp.catalog.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PagingRuleTest {

  private static final int PAGE_SIZE = 100;

  @ParameterizedTest
  @CsvSource({"3, 3", "' 3 ', 3", "0, 0", "-2, 0"})
  void a_usable_reported_total_is_followed(String reported, int expectedTotal) {
    assertThat(PagingRule.from(Optional.of(reported), PAGE_SIZE))
        .isEqualTo(new PagingRule.KnownTotal(expectedTotal));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "many", "3.5"})
  @DisplayName("§3.4 no false completeness: an unusable total means paging until a short page")
  void an_unusable_reported_total_means_until_short_page(String reported) {
    assertThat(PagingRule.from(Optional.of(reported), PAGE_SIZE))
        .isEqualTo(new PagingRule.UntilShortPage(PAGE_SIZE));
  }

  @Test
  @DisplayName("§3.4 no false completeness: a missing total means paging until a short page")
  void a_missing_total_means_until_short_page() {
    assertThat(PagingRule.from(Optional.empty(), PAGE_SIZE))
        .isEqualTo(new PagingRule.UntilShortPage(PAGE_SIZE));
  }

  @Test
  @DisplayName("AC-CAT-2: a known total has more until it has been reached")
  void known_total_has_more_until_reached() {
    PagingRule rule = new PagingRule.KnownTotal(3);

    assertThat(rule.hasMore(2, PAGE_SIZE)).isTrue();
    assertThat(rule.hasMore(3, PAGE_SIZE)).isFalse();
    assertThat(rule.hasMore(4, PAGE_SIZE)).isFalse();
  }

  @Test
  @DisplayName("AC-CAT-3: an empty known total has no more")
  void known_total_of_zero_has_no_more() {
    assertThat(new PagingRule.KnownTotal(0).hasMore(1, 0)).isFalse();
  }

  @Test
  void until_short_page_has_more_after_a_full_page() {
    assertThat(new PagingRule.UntilShortPage(PAGE_SIZE).hasMore(1, PAGE_SIZE)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(ints = {PAGE_SIZE - 1, 30, 0})
  void until_short_page_stops_on_a_short_or_empty_page(int received) {
    assertThat(new PagingRule.UntilShortPage(PAGE_SIZE).hasMore(1, received)).isFalse();
  }
}
