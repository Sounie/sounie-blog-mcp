package nz.sounie.blogmcp.search.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Optional inclusive {@code from} and {@code to} calendar dates in the blog time zone {@link
 * #ZONE}. An absent bound is held as {@link LocalDate#MIN} or {@link LocalDate#MAX}.
 */
public final class PublishedDateRange {

  /** The blog time zone: NZST (UTC+12) or NZDT (UTC+13). */
  public static final ZoneId ZONE = ZoneId.of("Pacific/Auckland");

  private final LocalDate from;
  private final LocalDate to;

  private PublishedDateRange(LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new InvalidSearchQuery(
          InvalidSearchQuery.Reason.FROM_AFTER_TO, "From " + from + " is after to " + to);
    }
    this.from = from;
    this.to = to;
  }

  public static PublishedDateRange unbounded() {
    return new PublishedDateRange(LocalDate.MIN, LocalDate.MAX);
  }

  public static PublishedDateRange from(LocalDate from) {
    return new PublishedDateRange(from, LocalDate.MAX);
  }

  public static PublishedDateRange to(LocalDate to) {
    return new PublishedDateRange(LocalDate.MIN, to);
  }

  /**
   * @throws InvalidSearchQuery with reason {@code FROM_AFTER_TO} if {@code from} is after {@code
   *     to}
   */
  public static PublishedDateRange between(LocalDate from, LocalDate to) {
    return new PublishedDateRange(from, to);
  }

  /** Whether the instant's calendar date in {@link #ZONE} is within the range. */
  public boolean includes(Instant publishedAt) {
    LocalDate day = LocalDate.ofInstant(publishedAt, ZONE);
    return !day.isBefore(from) && !day.isAfter(to);
  }
}
