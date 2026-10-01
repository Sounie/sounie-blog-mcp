package nz.sounie.blogmcp.catalog.domain;

import java.time.Duration;
import java.util.Objects;

/** How far before the checkpoint an incremental sync starts asking for changes. */
public record OverlapMargin(Duration value) {

  /** The fixed margin of one hour (owner decision Q4). */
  public static final OverlapMargin STANDARD = new OverlapMargin(Duration.ofHours(1));

  public OverlapMargin {
    Objects.requireNonNull(value, "overlap margin");
    if (value.isNegative()) {
      throw new IllegalArgumentException("Overlap margin must not be negative: " + value);
    }
  }
}
