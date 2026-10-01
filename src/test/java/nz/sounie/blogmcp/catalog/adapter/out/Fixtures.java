package nz.sounie.blogmcp.catalog.adapter.out;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.catalog.domain.BlogSource;
import nz.sounie.blogmcp.catalog.domain.PageCursor;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.SourcePage;

/** Recorded and generated platform responses for adapter tests. */
final class Fixtures {

  private Fixtures() {}

  /** Reads {@code src/test/resources/fixtures/<path>}. */
  static String read(String path) {
    try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + path)) {
      if (in == null) {
        throw new IllegalArgumentException("No fixture " + path);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A WordPress posts page of {@code count} minimal posts with IDs from {@code firstId}. */
  static String wordPressPosts(int firstId, int count) {
    return IntStream.range(firstId, firstId + count)
        .mapToObj(
            id ->
                """
                {"id":%d,"date_gmt":"2026-01-01T00:00:00","modified_gmt":"%s",\
                "link":"https://blog2.sounie.nz/post-%d/","status":"publish","type":"post",\
                "title":{"rendered":"Post %d"},\
                "content":{"rendered":"<p>Body %d</p>","protected":false},\
                "tags":[],"categories":[]}"""
                    .formatted(
                        id,
                        Instant.parse("2026-02-01T00:00:00Z")
                            .plusSeconds(id)
                            .toString()
                            .replace("Z", ""),
                        id,
                        id,
                        id))
        .collect(Collectors.joining(",", "[", "]"));
  }

  /** A Blogger feed page of {@code count} minimal entries, numbered from {@code firstNumber}. */
  static String bloggerFeed(int totalResults, int startIndex, int firstNumber, int count) {
    String entries =
        IntStream.range(firstNumber, firstNumber + count)
            .mapToObj(
                n ->
                    """
                    {"id":{"$t":"tag:blogger.com,1999:blog-1481195935080195479.post-%d"},\
                    "published":{"$t":"2020-01-01T00:00:00.000+00:00"},\
                    "updated":{"$t":"%s"},\
                    "title":{"type":"text","$t":"Post %d"},\
                    "summary":{"type":"text","$t":"Summary %d"},\
                    "link":[{"rel":"alternate","type":"text/html",\
                    "href":"https://blog.elegant-solutions.london/2020/01/post-%d.html"}]}"""
                        .formatted(
                            9_000_000L + n,
                            Instant.parse("2026-04-13T00:00:00Z").minusSeconds(n),
                            n,
                            n,
                            n))
            .collect(Collectors.joining(","));
    return """
        {"version":"1.0","encoding":"UTF-8","feed":{\
        "openSearch$totalResults":{"$t":"%d"},\
        "openSearch$startIndex":{"$t":"%d"},\
        "openSearch$itemsPerPage":{"$t":"150"},\
        "entry":[%s]}}"""
        .formatted(totalResults, startIndex, entries);
  }

  /** Follows a source's pages from the first, with a safety cap. */
  static List<SourceEntry> fetchAll(BlogSource source, Site site, Optional<Instant> changedSince) {
    List<SourceEntry> entries = new ArrayList<>();
    Optional<PageCursor> cursor = Optional.of(PageCursor.first());
    for (int pages = 0; cursor.isPresent() && pages < 10; pages++) {
      SourcePage page = source.fetch(site, changedSince, cursor.get());
      entries.addAll(page.entries());
      cursor = page.next();
    }
    return entries;
  }
}
