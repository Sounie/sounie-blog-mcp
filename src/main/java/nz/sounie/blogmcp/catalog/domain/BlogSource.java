package nz.sounie.blogmcp.catalog.domain;

import java.time.Instant;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.Site;
import nz.sounie.blogmcp.catalog.domain.sync.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.sync.PageCursor;
import nz.sounie.blogmcp.catalog.domain.sync.SourcePage;
import nz.sounie.blogmcp.catalog.domain.sync.SourceUnavailable;

/**
 * Port: pages through the source entries of a site.
 *
 * <p>Contract: {@code changedSince} must be sent to the platform with an explicit UTC offset
 * ({@code Z} or {@code +00:00}), never as a zone-less local date-time.
 */
public interface BlogSource {

  ChangeOrder changeOrder();

  /**
   * @param changedSince only entries changed since this instant, or every entry when empty
   * @throws SourceUnavailable on transport or HTTP failure
   */
  SourcePage fetch(Site site, Optional<Instant> changedSince, PageCursor cursor);
}
