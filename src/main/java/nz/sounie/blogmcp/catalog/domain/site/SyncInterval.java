package nz.sounie.blogmcp.catalog.domain.site;

import java.time.Duration;

/**
 * How long to wait between the end of one sync-and-reconcile run and the start of the next. At
 * least {@link #MINIMUM}; {@link #DEFAULT} when the configuration does not say.
 */
public record SyncInterval(Duration value) {

  public static final Duration MINIMUM = Duration.ofHours(1);

  /** 24 hours. */
  public static final SyncInterval DEFAULT = new SyncInterval(Duration.ofHours(24));

  /**
   * @throws IllegalArgumentException if the value is shorter than {@link #MINIMUM}
   */
  public SyncInterval {
    // red: the implementer adds the minimum check (app.md 3.8)
  }

  public static SyncInterval ofHours(long hours) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.8)");
  }
}
