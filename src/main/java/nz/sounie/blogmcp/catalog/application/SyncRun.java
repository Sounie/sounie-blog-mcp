package nz.sounie.blogmcp.catalog.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.catalog.domain.BlogSource;
import nz.sounie.blogmcp.catalog.domain.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.OverlapMargin;
import nz.sounie.blogmcp.catalog.domain.PageCursor;
import nz.sounie.blogmcp.catalog.domain.Post;
import nz.sounie.blogmcp.catalog.domain.PostId;
import nz.sounie.blogmcp.catalog.domain.PostRepository;
import nz.sounie.blogmcp.catalog.domain.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.Revision;
import nz.sounie.blogmcp.catalog.domain.Site;
import nz.sounie.blogmcp.catalog.domain.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.SourcePage;
import nz.sounie.blogmcp.catalog.domain.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.SourceUnavailable;
import nz.sounie.blogmcp.catalog.domain.SyncCheckpoint;
import nz.sounie.blogmcp.catalog.domain.SyncCheckpointRepository;
import nz.sounie.blogmcp.catalog.domain.WithdrawalReason;
import nz.sounie.blogmcp.shared.event.IntegrationEventPublisher;

/**
 * One sync of one site. Holds the state of the run (counts, skipped entries, the high-water mark)
 * and is used once. Handles one post per save and publishes each event after its save.
 */
final class SyncRun {

  private final Site site;
  private final SyncMode mode;
  private final BlogSource source;
  private final PostRepository posts;
  private final SyncCheckpointRepository checkpoints;
  private final IntegrationEventPublisher events;
  private final PostWithdrawals withdrawals;
  private final Clock clock;

  private final SyncCheckpoint checkpoint;
  private final Optional<Instant> checkpointBefore;

  private int pagesFetched;
  private int entriesSeen;
  private int published;
  private int revised;
  private int unchanged;
  private int withdrawn;
  private final List<SkippedEntry> skipped = new ArrayList<>();
  private final List<SyncWarning> warnings = new ArrayList<>();
  private final Set<SourcePostId> listed = new HashSet<>();
  private boolean unidentifiedEntrySeen;
  private Optional<Instant> highWaterMark = Optional.empty();

  SyncRun(
      Site site,
      SyncMode mode,
      BlogSource source,
      PostRepository posts,
      SyncCheckpointRepository checkpoints,
      IntegrationEventPublisher events,
      Clock clock) {
    this.site = site;
    this.mode = mode;
    this.source = source;
    this.posts = posts;
    this.checkpoints = checkpoints;
    this.events = events;
    this.withdrawals = new PostWithdrawals(posts, events);
    this.clock = clock;
    this.checkpoint = checkpoints.find(site.id()).orElseGet(() -> SyncCheckpoint.start(site.id()));
    this.checkpointBefore = checkpoint.changesSeenUpTo();
  }

  SyncReport run() {
    try {
      fetchEveryPage();
    } catch (SourceUnavailable e) {
      SyncOutcome outcome = pagesFetched == 0 ? SyncOutcome.FAILED : SyncOutcome.PARTIAL;
      return report(outcome, Optional.of(e.getMessage()));
    }
    advanceCheckpoint();
    if (mode == SyncMode.RECONCILE) {
      withdrawUnlistedPosts();
    }
    return report(SyncOutcome.COMPLETED, Optional.empty());
  }

  private void fetchEveryPage() {
    Optional<Instant> changedSince =
        mode == SyncMode.RECONCILE
            ? Optional.empty()
            : checkpoint.changesSinceForNextSync(OverlapMargin.STANDARD);
    Optional<PageCursor> cursor = Optional.of(PageCursor.first());
    while (cursor.isPresent()) {
      SourcePage page = source.fetch(site, changedSince, cursor.get());
      page.entries().forEach(this::handle);
      pagesFetched++;
      if (source.changeOrder() == ChangeOrder.OLDEST_FIRST) {
        advanceCheckpoint();
      }
      cursor = page.next();
    }
  }

  // --- entries ---------------------------------------------------------------------------------

  private void handle(SourceEntry entry) {
    entriesSeen++;
    entry.updatedAt().ifPresent(this::noteSourceTimestamp);
    switch (entry) {
      case SourceEntry.Available available -> handleAvailable(available);
      case SourceEntry.NotPublic notPublic -> handleNotPublic(notPublic);
      case SourceEntry.Malformed malformed -> handleMalformed(malformed);
    }
  }

  private void handleAvailable(SourceEntry.Available available) {
    PostSnapshot snapshot = available.snapshot();
    SourcePostId sourcePostId = snapshot.id().sourcePostId();
    listed.add(sourcePostId);
    available
        .notes()
        .forEach(
            note ->
                warnings.add(
                    new SyncWarning(
                        SyncWarning.Kind.SOURCE_NOTE, Optional.of(sourcePostId), note)));
    Optional<String> malformation = malformationOf(snapshot);
    if (malformation.isPresent()) {
      skip(Optional.of(sourcePostId), SkipReason.MALFORMED, malformation.get());
    } else if (urlHeldByAnotherPost(snapshot)) {
      skip(
          Optional.of(sourcePostId),
          SkipReason.DUPLICATE_CANONICAL_URL,
          "canonical URL " + snapshot.url().value() + " belongs to another post");
    } else {
      posts
          .findById(snapshot.id())
          .ifPresentOrElse(post -> revise(post, snapshot), () -> publish(snapshot));
    }
  }

  private Optional<String> malformationOf(PostSnapshot snapshot) {
    if (!snapshot.id().siteId().equals(site.id())) {
      return Optional.of("post " + snapshot.id().external() + " is not on " + site.id().value());
    }
    if (!snapshot.url().isOn(site)) {
      return Optional.of(
          "canonical URL " + snapshot.url().value() + " is not on the site host " + site.host());
    }
    return Optional.empty();
  }

  private boolean urlHeldByAnotherPost(PostSnapshot snapshot) {
    return posts
        .findByCanonicalUrl(snapshot.url())
        .filter(holder -> !holder.id().equals(snapshot.id()))
        .isPresent();
  }

  private void publish(PostSnapshot snapshot) {
    Post.Published publication = Post.publish(site, snapshot);
    posts.save(publication.post());
    events.publish(IntegrationEvents.from(publication.event()));
    published++;
  }

  private void revise(Post post, PostSnapshot snapshot) {
    switch (post.revise(snapshot)) {
      case Revision.Changed changed -> {
        posts.save(post);
        events.publish(IntegrationEvents.from(changed.event()));
        revised++;
      }
      case Revision.Touched _ -> {
        posts.save(post);
        unchanged++;
      }
      case Revision.Unchanged _, Revision.Stale _ -> unchanged++;
    }
  }

  private void handleNotPublic(SourceEntry.NotPublic notPublic) {
    listed.add(notPublic.sourcePostId());
    posts
        .findById(new PostId(site.id(), notPublic.sourcePostId()))
        .ifPresentOrElse(
            post -> withdraw(post, WithdrawalReason.NO_LONGER_PUBLIC),
            () ->
                skip(
                    Optional.of(notPublic.sourcePostId()),
                    SkipReason.NOT_PUBLIC,
                    "the post is not public"));
  }

  private void handleMalformed(SourceEntry.Malformed malformed) {
    malformed.sourcePostId().ifPresentOrElse(listed::add, () -> unidentifiedEntrySeen = true);
    skip(malformed.sourcePostId(), SkipReason.MALFORMED, malformed.reason());
  }

  private void skip(Optional<SourcePostId> sourcePostId, SkipReason reason, String detail) {
    skipped.add(new SkippedEntry(sourcePostId, reason, detail));
  }

  private void withdraw(Post post, WithdrawalReason reason) {
    withdrawals.withdraw(post, reason);
    withdrawn++;
  }

  // --- checkpoint ------------------------------------------------------------------------------

  private void noteSourceTimestamp(Instant updatedAt) {
    if (highWaterMark.isEmpty() || updatedAt.isAfter(highWaterMark.get())) {
      highWaterMark = Optional.of(updatedAt);
    }
  }

  /** Moves the checkpoint to the high-water mark and saves it, if that moves it forward. */
  private void advanceCheckpoint() {
    if (highWaterMark.isEmpty()) {
      return;
    }
    Optional<Instant> before = checkpoint.changesSeenUpTo();
    checkpoint.advanceTo(highWaterMark.get());
    if (!checkpoint.changesSeenUpTo().equals(before)) {
      checkpoints.save(checkpoint);
    }
  }

  // --- reconcile -------------------------------------------------------------------------------

  /**
   * After a complete reconcile, withdraws every stored post of the site that was not listed. Does
   * nothing, and leaves the reconcile due, when the listing was empty or contained an entry whose
   * source post ID could not be read: either could make a listed post look missing.
   */
  private void withdrawUnlistedPosts() {
    if (entriesSeen == 0) {
      warn(
          SyncWarning.Kind.EMPTY_LISTING_WITHDRAWALS_SUPPRESSED,
          "the reconcile listed no entries, so no post was withdrawn");
      return;
    }
    if (unidentifiedEntrySeen) {
      warn(
          SyncWarning.Kind.UNIDENTIFIED_MALFORMED_ENTRY_WITHDRAWALS_SUPPRESSED,
          "a malformed entry had no readable source post ID, so no post was withdrawn");
      return;
    }
    for (PostId id : posts.findIdsBySite(site.id())) {
      if (!listed.contains(id.sourcePostId())) {
        posts.findById(id).ifPresent(post -> withdraw(post, WithdrawalReason.NO_LONGER_LISTED));
      }
    }
    checkpoint.markReconciled(clock.instant());
    checkpoints.save(checkpoint);
  }

  private void warn(SyncWarning.Kind kind, String detail) {
    warnings.add(new SyncWarning(kind, Optional.empty(), detail));
  }

  private SyncReport report(SyncOutcome outcome, Optional<String> error) {
    return new SyncReport(
        site.id(),
        mode,
        outcome,
        pagesFetched,
        published,
        revised,
        unchanged,
        withdrawn,
        skipped,
        warnings,
        error,
        checkpointBefore,
        checkpoint.changesSeenUpTo());
  }
}
