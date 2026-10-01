package nz.sounie.blogmcp.catalog.application;

import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.SourcePostId;

/** A source entry that was not applied, with its source post ID if it could be read. */
public record SkippedEntry(Optional<SourcePostId> sourcePostId, SkipReason reason, String detail) {}
