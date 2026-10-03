package nz.sounie.blogmcp.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import nz.sounie.blogmcp.search.domain.embedding.Similarity;
import nz.sounie.blogmcp.search.domain.index.PostMatch;
import nz.sounie.blogmcp.search.domain.index.Snippet;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** app.md 3.1: score and published-date presentation. */
class SearchResultViewTest {

  private static PostMatch match(Instant publishedAt, double score) {
    SiteId site = new SiteId("elegant");
    return new PostMatch(
        new PostId(site, "7"),
        new PostMetadata(
            site,
            "https://blog.elegant-solutions.london/2024/04/records.html",
            "Records",
            Set.of("java"),
            publishedAt,
            publishedAt),
        new Similarity(score),
        new Snippet("Records are …", true));
  }

  @Test
  void presents_the_match() {
    SearchResultView view = SearchResultView.of(match(Instant.parse("2024-06-01T00:00:00Z"), 0.5));

    assertThat(view)
        .isEqualTo(
            new SearchResultView(
                "elegant:7",
                "Records",
                "https://blog.elegant-solutions.london/2024/04/records.html",
                "elegant",
                "2024-06-01",
                0.5,
                "Records are …"));
  }

  @ParameterizedTest
  @CsvSource({
    "2024-03-31T11:30:00Z, 2024-04-01", // 00:30 NZDT on 1 April
    "2024-03-31T10:59:59Z, 2024-03-31", // 23:59:59 NZDT
    "2024-07-01T11:59:59Z, 2024-07-01", // 23:59:59 NZST
    "2024-07-01T12:00:00Z, 2024-07-02"
  })
  void the_published_date_is_the_calendar_date_in_the_blog_time_zone(
      String publishedAt, String date) {
    assertThat(SearchResultView.of(match(Instant.parse(publishedAt), 0.5)).published())
        .isEqualTo(date);
  }

  @ParameterizedTest
  @CsvSource({
    "0.12345, 0.123",
    "0.8765, 0.877",
    "0.0005, 0.001",
    "0.89999997615814, 0.9",
    "-0.1234, -0.123",
    "1.0, 1.0"
  })
  void the_score_is_rounded_half_up_to_three_decimals(double similarity, double shown) {
    assertThat(
            SearchResultView.of(match(Instant.parse("2024-06-01T00:00:00Z"), similarity)).score())
        .isEqualTo(shown);
  }
}
