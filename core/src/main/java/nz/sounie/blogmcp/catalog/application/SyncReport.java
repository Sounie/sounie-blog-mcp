package nz.sounie.blogmcp.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;

/** Outcome of one sync of one site. */
public record SyncReport(
    SiteId siteId,
    SyncMode mode,
    SyncOutcome outcome,
    int pagesFetched,
    int published,
    int revised,
    int unchanged,
    int withdrawn,
    List<SkippedEntry> skipped,
    List<SyncWarning> warnings,
    Optional<String> error,
    Optional<Instant> checkpointBefore,
    Optional<Instant> checkpointAfter) {

  public SyncReport {
    Objects.requireNonNull(siteId, "site ID");
    Objects.requireNonNull(mode, "mode");
    Objects.requireNonNull(outcome, "outcome");
    skipped = List.copyOf(skipped);
    warnings = List.copyOf(warnings);
    Objects.requireNonNull(error, "error");
    Objects.requireNonNull(checkpointBefore, "checkpointBefore");
    Objects.requireNonNull(checkpointAfter, "checkpointAfter");
  }

  /** A sync that did not run because another sync of the same site was in progress. */
  static SyncReport skipped(SiteId siteId, SyncMode mode) {
    return withoutWork(siteId, mode, SyncOutcome.SKIPPED, Optional.empty());
  }

  /** A sync that broke before handling any page, for a reason other than the source. */
  static SyncReport failed(SiteId siteId, SyncMode mode, String error) {
    return withoutWork(siteId, mode, SyncOutcome.FAILED, Optional.of(error));
  }

  private static SyncReport withoutWork(
      SiteId siteId, SyncMode mode, SyncOutcome outcome, Optional<String> error) {
    return new SyncReport(
        siteId,
        mode,
        outcome,
        0,
        0,
        0,
        0,
        0,
        List.of(),
        List.of(),
        error,
        Optional.empty(),
        Optional.empty());
  }
}
