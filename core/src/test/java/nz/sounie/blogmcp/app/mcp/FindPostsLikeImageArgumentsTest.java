package nz.sounie.blogmcp.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import nz.sounie.blogmcp.search.domain.image.ImageQuery;
import nz.sounie.blogmcp.search.domain.image.InvalidImageQuery;
import nz.sounie.blogmcp.search.domain.post.SiteId;
import nz.sounie.blogmcp.search.domain.query.ImageSearchQuery;
import nz.sounie.blogmcp.search.domain.query.PublishedDateRange;
import nz.sounie.blogmcp.search.domain.query.ResultLimit;
import nz.sounie.blogmcp.search.domain.query.SiteFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * app.md 3.9: {@code find_posts_like_image} arguments to an {@link ImageSearchQuery}. The image
 * rules are {@code ImageQuery}'s (AC-SRCH-81) and the scope rules are {@code ScopeArguments}'
 * (shared with {@code search_posts}, covered by {@code SearchPostsArgumentsTest}); only the wiring
 * is checked here.
 */
class FindPostsLikeImageArgumentsTest {

  private final SiteChoices sites = SiteChoices.of(List.of("sounie-wp", "elegant"));

  @TempDir Path dir;

  private Path swan;

  @BeforeEach
  void givenASwanFile() throws IOException {
    swan = Files.write(dir.resolve("swan.png"), new byte[100]);
  }

  private ImageSearchQuery queryFor(Map<String, Object> raw) {
    return FindPostsLikeImageArguments.read(raw).toQuery(sites);
  }

  private static Instant startOf(LocalDate day) {
    return day.atStartOfDay(PublishedDateRange.ZONE).toInstant();
  }

  private static Instant endOf(LocalDate day) {
    return day.atTime(LocalTime.MAX).atZone(PublishedDateRange.ZONE).toInstant();
  }

  @Test
  @DisplayName("AC-APP-52: a local path and every scope argument reach the query")
  void maps_every_argument() {
    ImageSearchQuery query =
        queryFor(
            Map.of(
                "image", swan.toString(),
                "site", "elegant",
                "from", "2024-04-01",
                "to", "2024-12-31",
                "limit", 5));

    assertThat(query.image()).isEqualTo(new ImageQuery.LocalImage(swan));
    assertThat(query.scope().site()).isEqualTo(new SiteFilter.OnlySite(new SiteId("elegant")));
    assertThat(query.scope().limit()).isEqualTo(ResultLimit.of(5));
    PublishedDateRange dates = query.scope().dates();
    assertThat(dates.includes(startOf(LocalDate.of(2024, 4, 1)))).isTrue();
    assertThat(dates.includes(endOf(LocalDate.of(2024, 3, 31)))).isFalse();
    assertThat(dates.includes(endOf(LocalDate.of(2024, 12, 31)))).isTrue();
    assertThat(dates.includes(startOf(LocalDate.of(2025, 1, 1)))).isFalse();
  }

  @Test
  @DisplayName(
      "AC-APP-52: only a URL means a remote image, any site, any date and the default limit")
  void defaults_everything_but_the_image() {
    ImageSearchQuery query = queryFor(Map.of("image", "https://images.example.com/swan.jpg"));

    assertThat(query.image())
        .isEqualTo(new ImageQuery.RemoteImage(URI.create("https://images.example.com/swan.jpg")));
    assertThat(query.scope().site()).isEqualTo(new SiteFilter.AnySite());
    assertThat(query.scope().limit()).isEqualTo(ResultLimit.defaultLimit());
    assertThat(query.scope().dates().includes(Instant.parse("1970-01-01T00:00:00Z"))).isTrue();
    assertThat(query.scope().dates().includes(Instant.parse("2999-12-31T00:00:00Z"))).isTrue();
  }

  @Test
  @DisplayName("AC-APP-52: a limit of 50 is clamped to 20")
  void clamps_the_limit() {
    ImageSearchQuery query =
        queryFor(Map.of("image", "https://images.example.com/swan.jpg", "limit", 50));

    assertThat(query.scope().limit()).isEqualTo(ResultLimit.of(20));
  }

  @Test
  @DisplayName("AC-APP-52: the image is required")
  void the_image_is_required() {
    assertThatThrownBy(() -> FindPostsLikeImageArguments.read(Map.of("site", "elegant")))
        .isInstanceOf(InvalidToolArgument.class)
        .hasMessageContaining("image");
  }

  @Test
  @DisplayName("AC-APP-52: search_posts' query argument is not one of this tool's arguments")
  void rejects_unknown_argument_names() {
    assertThatThrownBy(
            () ->
                FindPostsLikeImageArguments.read(
                    Map.of("image", "https://images.example.com/swan.jpg", "query", "swans")))
        .isInstanceOf(InvalidToolArgument.class)
        .hasMessageContaining("query");
  }

  @Test
  @DisplayName("AC-APP-52: a bad image is refused when the query is built")
  void a_bad_image_is_refused_by_the_image_query() {
    FindPostsLikeImageArguments arguments =
        FindPostsLikeImageArguments.read(Map.of("image", dir.resolve("missing.png").toString()));

    assertThatThrownBy(() -> arguments.toQuery(sites))
        .isInstanceOfSatisfying(
            InvalidImageQuery.class,
            e -> assertThat(e.reason()).isEqualTo(InvalidImageQuery.Reason.NOT_A_READABLE_FILE));
  }
}
