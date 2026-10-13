package nz.sounie.blogmcp.app.mcp;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import nz.sounie.blogmcp.search.domain.index.PostMatch;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;
import nz.sounie.blogmcp.search.domain.query.PublishedDateRange;

/**
 * The JSON shape of one {@code search_posts} result.
 *
 * @param published the {@code yyyy-MM-dd} publication date in the blog time zone
 * @param score the similarity rounded half-up to 3 decimals
 * @param matchedImage the matched image's URL when the best entry is an image; otherwise null, and
 *     the key is omitted from the JSON
 */
public record SearchResultView(
    String postId,
    String title,
    String url,
    String site,
    String published,
    double score,
    String snippet,
    @JsonInclude(JsonInclude.Include.NON_NULL) String matchedImage) {

  private static final int SCORE_DECIMALS = 3;

  public static SearchResultView of(PostMatch match) {
    PostMetadata metadata = match.metadata();
    return new SearchResultView(
        match.postId().external(),
        metadata.title(),
        metadata.canonicalUrl(),
        metadata.siteId().value(),
        publishedDate(metadata.publishedAt()),
        BigDecimal.valueOf(match.score().value())
            .setScale(SCORE_DECIMALS, RoundingMode.HALF_UP)
            .doubleValue(),
        match.snippet().text(),
        match.matchedImage().map(URI::toString).orElse(null));
  }

  /** The calendar date in the blog time zone, so it matches the date the filter uses. */
  static String publishedDate(Instant publishedAt) {
    return LocalDate.ofInstant(publishedAt, PublishedDateRange.ZONE).toString();
  }
}
