package nz.sounie.blogmcp.catalog.domain;

import java.util.List;
import java.util.Optional;

/** One page of source entries, plus the cursor of the next page if more pages follow. */
public record SourcePage(List<SourceEntry> entries, Optional<PageCursor> next) {

  public boolean hasMore() {
    return next.isPresent();
  }
}
