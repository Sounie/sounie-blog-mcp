package nz.sounie.blogmcp.catalog.adapter.out;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Writes a post file exactly as the slice 3 release stored it ({@code "format": 1}, no {@code
 * images}), so tests can check that later versions still read it (Q9).
 */
public final class FormatOnePostFile {

  private FormatOnePostFile() {}

  /**
   * Writes {@code <data>/catalog/posts/<siteId>/<sourcePostId>.json} for a post with title {@code
   * Same}, body {@code Same body.}, tag {@code DDD}, published and updated at {@code at}. The
   * source post ID must be a plain file key (letters and digits).
   *
   * @return the file written
   */
  public static Path write(
      Path dataDirectory, String siteId, String sourcePostId, String canonicalUrl, Instant at) {
    Path file =
        dataDirectory
            .resolve("catalog")
            .resolve("posts")
            .resolve(siteId)
            .resolve(sourcePostId + ".json");
    String json =
        """
        {"format":1,"postId":"%s:%s","canonicalUrl":"%s","title":"Same","body":"Same body.",\
        "completeness":"FULL","tags":["DDD"],"publishedAt":"%s","updatedAt":"%s"}"""
            .formatted(siteId, sourcePostId, canonicalUrl, at, at);
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return file;
  }
}
