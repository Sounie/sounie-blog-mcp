package nz.sounie.blogmcp.search.domain.query;

import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import org.junit.jupiter.api.Test;

class SearchFiltersTest {

  private static final Instant IN_RANGE = Instant.parse("2024-04-10T00:00:00Z");
  private static final Instant BEFORE = Instant.parse("2024-03-10T00:00:00Z");

  private final SearchFilters elegantInApril =
      SearchFilters.of(
          new SiteFilter.OnlySite(new SiteId("elegant")),
          PublishedDateRange.from(LocalDate.of(2024, 4, 1)));

  private static PostMetadata metadata(String id, Instant publishedAt) {
    return aPost().id(id).publishedAt(publishedAt).metadata();
  }

  @Test
  void includes_when_every_rule_holds() {
    assertThat(elegantInApril.includes(metadata("elegant:1", IN_RANGE))).isTrue();
  }

  @Test
  void excludes_when_the_date_rule_fails() {
    assertThat(elegantInApril.includes(metadata("elegant:1", BEFORE))).isFalse();
  }

  @Test
  void excludes_when_the_site_rule_fails() {
    assertThat(elegantInApril.includes(metadata("sounie-wp:1", IN_RANGE))).isFalse();
  }

  @Test
  void any_site_and_unbounded_dates_include_everything() {
    SearchFilters none = SearchFilters.of(new SiteFilter.AnySite(), PublishedDateRange.unbounded());

    assertThat(none.includes(metadata("sounie-wp:1", BEFORE))).isTrue();
  }

  @Test
  void a_search_query_composes_its_filters() {
    SearchQuery query = SearchQueryBuilder.aQuery("records").onlySite("elegant").build();

    assertThat(query.filters().includes(metadata("elegant:1", BEFORE))).isTrue();
    assertThat(query.filters().includes(metadata("sounie-wp:1", BEFORE))).isFalse();
  }
}
