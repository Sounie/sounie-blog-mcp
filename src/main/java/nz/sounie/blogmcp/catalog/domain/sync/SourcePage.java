package nz.sounie.blogmcp.catalog.domain.sync;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One page of source entries, plus the cursor of the next page if more pages follow. */
public record SourcePage(List<SourceEntry> entries, Optional<PageCursor> next) {

  public SourcePage {
    entries = List.copyOf(entries);
    Objects.requireNonNull(next, "next");
  }

  public boolean hasMore() {
    return next.isPresent();
  }
}
