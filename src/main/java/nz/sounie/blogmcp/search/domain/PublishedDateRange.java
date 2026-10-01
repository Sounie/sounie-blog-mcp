package nz.sounie.blogmcp.search.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Optional inclusive {@code from} and {@code to} calendar dates in the blog time zone {@link
 * #ZONE}.
 */
public final class PublishedDateRange {

  /** The blog time zone: NZST (UTC+12) or NZDT (UTC+13). */
  public static final ZoneId ZONE = ZoneId.of("Pacific/Auckland");

  private PublishedDateRange() {}

  public static PublishedDateRange unbounded() {
    throw new UnsupportedOperationException("not implemented");
  }

  public static PublishedDateRange from(LocalDate from) {
    throw new UnsupportedOperationException("not implemented");
  }

  public static PublishedDateRange to(LocalDate to) {
    throw new UnsupportedOperationException("not implemented");
  }

  /**
   * @throws InvalidSearchQuery with reason {@code FROM_AFTER_TO} if {@code from} is after {@code
   *     to}
   */
  public static PublishedDateRange between(LocalDate from, LocalDate to) {
    throw new UnsupportedOperationException("not implemented");
  }

  /** Whether the instant's calendar date in {@link #ZONE} is within the range. */
  public boolean includes(Instant publishedAt) {
    throw new UnsupportedOperationException("not implemented");
  }
}
