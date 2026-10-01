package nz.sounie.blogmcp.catalog.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.SiteId;

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
    Optional<Instant> checkpointAfter) {}
