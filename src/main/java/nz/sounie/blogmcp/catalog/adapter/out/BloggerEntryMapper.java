package nz.sounie.blogmcp.catalog.adapter.out;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import nz.sounie.blogmcp.catalog.domain.Body;
import nz.sounie.blogmcp.catalog.domain.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.CanonicalUrlNotOnSite;
import nz.sounie.blogmcp.catalog.domain.HtmlToText;
import nz.sounie.blogmcp.catalog.domain.PostId;
import nz.sounie.blogmcp.catalog.domain.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.Tag;
import nz.sounie.blogmcp.catalog.domain.Title;
import tools.jackson.databind.JsonNode;

/**
 * Maps one Blogger feed entry to a source entry, never throwing for bad data. Each step reads one
 * field and either continues or ends with a malformed entry (no id, no updated or published time,
 * or no alternate link on the site).
 */
final class BloggerEntryMapper {

  private static final Pattern POST_ID = Pattern.compile("\\.post-(\\d+)$");
  private static final String TEXT = "$t";

  private final Site site;
  private final HtmlToText htmlToText;

  BloggerEntryMapper(Site site, HtmlToText htmlToText) {
    this.site = site;
    this.htmlToText = htmlToText;
  }

  SourceEntry map(JsonNode entry) {
    Optional<Instant> updated = instant(entry, "updated");
    return sourcePostId(entry)
        .map(id -> identified(entry, id, updated))
        .orElseGet(() -> malformed(Optional.empty(), "missing or invalid id", updated));
  }

  private SourceEntry identified(JsonNode entry, SourcePostId id, Optional<Instant> updated) {
    return updated
        .map(updatedAt -> dated(entry, id, updatedAt))
        .orElseGet(() -> malformed(Optional.of(id), "missing or invalid updated time", updated));
  }

  private SourceEntry dated(JsonNode entry, SourcePostId id, Instant updated) {
    return instant(entry, "published")
        .map(publishedAt -> linked(entry, id, publishedAt, updated))
        .orElseGet(
            () ->
                malformed(
                    Optional.of(id), "missing or invalid published time", Optional.of(updated)));
  }

  private SourceEntry linked(
      JsonNode entry, SourcePostId id, Instant publishedAt, Instant updated) {
    try {
      CanonicalUrl url = CanonicalUrl.onSite(site, alternateLink(entry));
      return available(entry, id, url, publishedAt, updated);
    } catch (CanonicalUrlNotOnSite e) {
      return malformed(Optional.of(id), e.getMessage(), Optional.of(updated));
    }
  }

  private SourceEntry available(
      JsonNode entry, SourcePostId id, CanonicalUrl url, Instant publishedAt, Instant updated) {
    BodyText body = BodyText.of(entry);
    JsonNode title = entry.path("title");
    return new SourceEntry.Available(
        new PostSnapshot(
            new PostId(site.id(), id),
            url,
            new Title(
                TitleFormat.ofAtomType(title.path("type").asString("text"))
                    .toPlainText(text(title), htmlToText)),
            new Body(htmlToText.extract(body.html())),
            body.completeness(),
            Tag.setOf(labels(entry)),
            publishedAt,
            updated));
  }

  private static SourceEntry malformed(
      Optional<SourcePostId> id, String reason, Optional<Instant> updatedAt) {
    return new SourceEntry.Malformed(id, reason, updatedAt);
  }

  /** The digits after {@code .post-} in the entry ID. */
  private static Optional<SourcePostId> sourcePostId(JsonNode entry) {
    Matcher matcher = POST_ID.matcher(text(entry.path("id")));
    return matcher.find() ? Optional.of(new SourcePostId(matcher.group(1))) : Optional.empty();
  }

  /** Blogger timestamps carry an explicit offset. */
  private static Optional<Instant> instant(JsonNode entry, String field) {
    try {
      return Optional.of(OffsetDateTime.parse(text(entry.path(field))).toInstant());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  /** The canonical URL is the {@code alternate} link; empty text if there is none. */
  private static String alternateLink(JsonNode entry) {
    return entry.path("link").values().stream()
        .filter(link -> link.path("rel").asString("").equals("alternate"))
        .findFirst()
        .map(link -> link.path("href").asString(""))
        .orElse("");
  }

  /** Labels come from {@code category[].term}. */
  private static List<String> labels(JsonNode entry) {
    return entry.path("category").values().stream()
        .map(category -> category.path("term").asString(""))
        .toList();
  }

  private static String text(JsonNode node) {
    return node.path(TEXT).asString("");
  }

  /** The full {@code content} when the feed carries it; otherwise the {@code summary}. */
  private record BodyText(String html, BodyCompleteness completeness) {

    static BodyText of(JsonNode entry) {
      JsonNode content = entry.path("content");
      return content.has(TEXT)
          ? new BodyText(text(content), BodyCompleteness.FULL)
          : new BodyText(text(entry.path("summary")), BodyCompleteness.SUMMARY);
    }
  }
}
