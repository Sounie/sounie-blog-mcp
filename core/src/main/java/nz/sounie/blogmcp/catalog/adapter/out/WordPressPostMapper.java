package nz.sounie.blogmcp.catalog.adapter.out;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.catalog.domain.post.Body;
import nz.sounie.blogmcp.catalog.domain.post.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrl;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrlNotOnSite;
import nz.sounie.blogmcp.catalog.domain.post.PostId;
import nz.sounie.blogmcp.catalog.domain.post.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.post.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.post.Tag;
import nz.sounie.blogmcp.catalog.domain.post.Title;
import nz.sounie.blogmcp.catalog.domain.site.Site;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;
import tools.jackson.databind.JsonNode;

/**
 * Maps one WordPress post object to a source entry, never throwing for bad data. Each step reads
 * one field and either continues or ends with the entry that field decides: malformed (no id, no
 * modified time, no date, or a link that is not on the site), not public, or available.
 */
final class WordPressPostMapper {

  private final Site site;
  private final Map<Long, String> tagNames;
  private final HtmlToText htmlToText;

  WordPressPostMapper(Site site, Map<Long, String> tagNames, HtmlToText htmlToText) {
    this.site = site;
    this.tagNames = tagNames;
    this.htmlToText = htmlToText;
  }

  SourceEntry map(JsonNode post) {
    Optional<Instant> modified = gmt(post, "modified_gmt");
    return sourcePostId(post)
        .map(id -> identified(post, id, modified))
        .orElseGet(() -> malformed(Optional.empty(), "missing or invalid id", modified));
  }

  private SourceEntry identified(JsonNode post, SourcePostId id, Optional<Instant> modified) {
    return modified
        .map(updatedAt -> dated(post, id, updatedAt))
        .orElseGet(() -> malformed(Optional.of(id), "missing or invalid modified_gmt", modified));
  }

  private SourceEntry dated(JsonNode post, SourcePostId id, Instant modified) {
    return isPublic(post)
        ? published(post, id, modified)
        : new SourceEntry.NotPublic(id, Optional.of(modified));
  }

  private SourceEntry published(JsonNode post, SourcePostId id, Instant modified) {
    return gmt(post, "date_gmt")
        .map(publishedAt -> linked(post, id, publishedAt, modified))
        .orElseGet(
            () -> malformed(Optional.of(id), "missing or invalid date_gmt", Optional.of(modified)));
  }

  private SourceEntry linked(
      JsonNode post, SourcePostId id, Instant publishedAt, Instant modified) {
    try {
      CanonicalUrl url = CanonicalUrl.onSite(site, post.path("link").asString(""));
      return available(post, id, url, publishedAt, modified);
    } catch (CanonicalUrlNotOnSite e) {
      return malformed(Optional.of(id), e.getMessage(), Optional.of(modified));
    }
  }

  private SourceEntry available(
      JsonNode post, SourcePostId id, CanonicalUrl url, Instant publishedAt, Instant modified) {
    List<String> notes = new ArrayList<>();
    PostSnapshot snapshot =
        new PostSnapshot(
            new PostId(site.id(), id),
            url,
            new Title(TitleFormat.HTML.toPlainText(rendered(post, "title"), htmlToText)),
            new Body(htmlToText.extract(rendered(post, "content"))),
            BodyCompleteness.FULL,
            tags(post, notes),
            publishedAt,
            modified);
    return new SourceEntry.Available(snapshot, notes);
  }

  private static SourceEntry malformed(
      Optional<SourcePostId> id, String reason, Optional<Instant> updatedAt) {
    return new SourceEntry.Malformed(id, reason, updatedAt);
  }

  /** The numeric {@code id}, or a non-blank string one. */
  private static Optional<SourcePostId> sourcePostId(JsonNode post) {
    JsonNode id = post.path("id");
    boolean readable = id.isIntegralNumber() || (id.isString() && !id.stringValue().isBlank());
    return readable ? Optional.of(new SourcePostId(id.asString())) : Optional.empty();
  }

  /** WordPress {@code *_gmt} fields are zone-less but mean UTC. */
  private static Optional<Instant> gmt(JsonNode post, String field) {
    return Optional.of(post.path(field))
        .filter(JsonNode::isString)
        .map(JsonNode::stringValue)
        .flatMap(WordPressPostMapper::utc);
  }

  private static Optional<Instant> utc(String zoneLess) {
    try {
      return Optional.of(LocalDateTime.parse(zoneLess).toInstant(ZoneOffset.UTC));
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }

  /** Published, and not password-protected. */
  private static boolean isPublic(JsonNode post) {
    boolean passwordProtected = post.path("content").path("protected").asBoolean(false);
    return !passwordProtected && post.path("status").asString("publish").equals("publish");
  }

  private static String rendered(JsonNode post, String field) {
    return post.path(field).path("rendered").asString("");
  }

  /** Resolves tag IDs to names; each unknown ID is dropped and noted. Categories are ignored. */
  private Set<Tag> tags(JsonNode post, List<String> notes) {
    List<String> labels = new ArrayList<>();
    post.path("tags")
        .values()
        .forEach(
            tagId ->
                tagName(tagId)
                    .ifPresentOrElse(
                        labels::add,
                        () -> notes.add("unknown tag id " + tagId.asString() + " was dropped")));
    return Tag.setOf(labels);
  }

  private Optional<String> tagName(JsonNode tagId) {
    return Optional.of(tagId)
        .filter(JsonNode::canConvertToLong)
        .map(JsonNode::longValue)
        .map(tagNames::get);
  }
}
