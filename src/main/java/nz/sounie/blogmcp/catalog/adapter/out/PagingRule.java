package nz.sounie.blogmcp.catalog.adapter.out;

import java.util.Optional;

/**
 * When a platform listing has more pages. Chosen once per response: a usable reported total is
 * followed; without one, a full page means more may follow, so a truncated listing is never
 * reported as complete (catalog.md 3.4).
 */
sealed interface PagingRule {

  /**
   * @param reached how far the listing has been read, in the unit of the total (pages or entries)
   * @param received how many entries the page just read held
   */
  boolean hasMore(int reached, int received);

  /** Chooses the rule from the reported total, if it is a usable count. */
  static PagingRule from(Optional<String> reportedTotal, int pageSize) {
    return reportedTotal
        .flatMap(PagingRule::count)
        .<PagingRule>map(KnownTotal::new)
        .orElseGet(() -> new UntilShortPage(pageSize));
  }

  private static Optional<Integer> count(String reported) {
    try {
      return Optional.of(Math.max(0, Integer.parseInt(reported.strip())));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /** The platform reported how much there is. */
  record KnownTotal(int total) implements PagingRule {
    @Override
    public boolean hasMore(int reached, int received) {
      return reached < total;
    }
  }

  /** No usable total: keep going while pages are full. */
  record UntilShortPage(int pageSize) implements PagingRule {
    @Override
    public boolean hasMore(int reached, int received) {
      return received >= pageSize;
    }
  }
}
