package nz.sounie.blogmcp.catalog.domain;

import java.time.Duration;

/** How far before the checkpoint an incremental sync starts asking for changes. */
public record OverlapMargin(Duration value) {

  /** The fixed margin of one hour (owner decision Q4). */
  public static final OverlapMargin STANDARD = new OverlapMargin(Duration.ofHours(1));
}
