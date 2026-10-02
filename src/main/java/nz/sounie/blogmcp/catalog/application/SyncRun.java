package nz.sounie.blogmcp.catalog.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.BlogSource;
import nz.sounie.blogmcp.catalog.domain.post.CanonicalUrlNotOnSite;
import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.PostId;
import nz.sounie.blogmcp.catalog.domain.post.PostRepository;
import nz.sounie.blogmcp.catalog.domain.post.PostSnapshot;
import nz.sounie.blogmcp.catalog.domain.post.Revision;
import nz.sounie.blogmcp.catalog.domain.post.SourcePostId;
import nz.sounie.blogmcp.catalog.domain.post.WithdrawalReason;
import nz.sounie.blogmcp.catalog.domain.site.Site;
import nz.sounie.blogmcp.catalog.domain.sync.PageCursor;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.sync.SourcePage;
import nz.sounie.blogmcp.catalog.domain.sync.SourceUnavailable;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpoint;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpointRepository;
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
  private final Listing listing = new Listing();

  private int pagesFetched;
  private int published;
  private int revised;
  private int unchanged;
  private int withdrawn;
  private final List<SkippedEntry> skipped = new ArrayList<>();
  private final List<SyncWarning> warnings = new ArrayList<>();
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
    source.changeOrder().afterLastPage(this::advanceCheckpoint);
    mode.afterCompleteRun(this::reconcile);
    return report(SyncOutcome.COMPLETED, Optional.empty());
  }

  private void fetchEveryPage() {
    Optional<Instant> changedSince = mode.changedSince(checkpoint);
    Optional<PageCursor> cursor = Optional.of(PageCursor.first());
    while (cursor.isPresent()) {
      SourcePage page = source.fetch(site, changedSince, cursor.get());
      page.entries().forEach(this::handle);
      pagesFetched++;
      source.changeOrder().afterPageHandled(this::advanceCheckpoint);
      cursor = page.next();
    }
  }

  // --- entries ---------------------------------------------------------------------------------

  private void handle(SourceEntry entry) {
    listing.record(entry);
    entry.updatedAt().ifPresent(this::noteSourceTimestamp);
    switch (entry) {
      case SourceEntry.Available available -> handleAvailable(available);
      case SourceEntry.NotPublic notPublic -> handleNotPublic(notPublic);
      case SourceEntry.Malformed malformed -> handleMalformed(malformed);
    }
  }

  private void handleAvailable(SourceEntry.Available available) {
    PostSnapshot snapshot = available.snapshot();
    available.notes().forEach(note -> warnSourceNote(snapshot.id().sourcePostId(), note));
    if (takesUrlOfAnotherPost(snapshot)) {
      skipAsDuplicate(snapshot);
    } else {
      apply(snapshot);
    }
  }

  /**
   * Cross-aggregate rule (AC-CAT-17): a canonical URL belongs to at most one post. Only a URL on
   * the site host can be a duplicate; an off-host URL is left for the domain to reject. Checking
   * before applying means a loaded post is never revised and then dropped.
   */
  private boolean takesUrlOfAnotherPost(PostSnapshot snapshot) {
    return snapshot.url().isOn(site)
        && posts
            .findByCanonicalUrl(snapshot.url())
            .filter(holder -> !holder.id().equals(snapshot.id()))
            .isPresent();
  }

  /**
   * Publishes or revises. The domain enforces the site host: an off-host URL is bad source data and
   * is skipped as malformed. {@code PostIdentityMismatch} is an adapter bug, so it propagates.
   */
  private void apply(PostSnapshot snapshot) {
    try {
      posts
          .findById(snapshot.id())
          .ifPresentOrElse(post -> revise(post, snapshot), () -> publish(snapshot));
    } catch (CanonicalUrlNotOnSite e) {
      skip(Optional.of(snapshot.id().sourcePostId()), SkipReason.MALFORMED, e.getMessage());
    }
  }

  private void publish(PostSnapshot snapshot) {
    Post.Published publication = Post.publish(site, snapshot);
    posts.save(publication.post());
    events.publish(IntegrationEvents.from(publication.event()));
    published++;
  }

  private void revise(Post post, PostSnapshot snapshot) {
    switch (post.revise(site, snapshot)) {
      case Revision.Changed changed -> saveRevision(post, changed);
      case Revision.Touched _ -> saveTimestamp(post);
      case Revision.Unchanged _, Revision.Stale _ -> unchanged++;
    }
  }

  private void saveRevision(Post post, Revision.Changed changed) {
    posts.save(post);
    events.publish(IntegrationEvents.from(changed.event()));
    revised++;
  }

  private void saveTimestamp(Post post) {
    posts.save(post);
    unchanged++;
  }

  private void handleNotPublic(SourceEntry.NotPublic notPublic) {
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
    skip(malformed.sourcePostId(), SkipReason.MALFORMED, malformed.reason());
  }

  private void skipAsDuplicate(PostSnapshot snapshot) {
    skip(
        Optional.of(snapshot.id().sourcePostId()),
        SkipReason.DUPLICATE_CANONICAL_URL,
        "canonical URL " + snapshot.url().value() + " belongs to another post");
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
    highWaterMark =
        Optional.of(highWaterMark.filter(mark -> mark.isAfter(updatedAt)).orElse(updatedAt));
  }

  /** Moves the checkpoint to the high-water mark, and saves it if that moved it forward. */
  private void advanceCheckpoint() {
    highWaterMark.ifPresent(this::advanceCheckpointTo);
  }

  private void advanceCheckpointTo(Instant mark) {
    Optional<Instant> before = checkpoint.changesSeenUpTo();
    checkpoint.advanceTo(mark);
    if (!checkpoint.changesSeenUpTo().equals(before)) {
      checkpoints.save(checkpoint);
    }
  }

  // --- reconcile -------------------------------------------------------------------------------

  /** After a complete reconcile, withdraws unlisted posts unless the listing cannot be trusted. */
  private void reconcile() {
    switch (listing.withdrawalDecision()) {
      case WithdrawalDecision.Withdraw _ -> withdrawUnlistedPosts();
      case WithdrawalDecision.Suppress suppress -> warn(suppress.warning(), suppress.detail());
    }
  }

  private void withdrawUnlistedPosts() {
    posts.findIdsBySite(site.id()).stream()
        .filter(id -> !listing.lists(id.sourcePostId()))
        .map(posts::findById)
        .flatMap(Optional::stream)
        .forEach(post -> withdraw(post, WithdrawalReason.NO_LONGER_LISTED));
    checkpoint.markReconciled(clock.instant());
    checkpoints.save(checkpoint);
  }

  private void warnSourceNote(SourcePostId sourcePostId, String note) {
    warnings.add(new SyncWarning(SyncWarning.Kind.SOURCE_NOTE, Optional.of(sourcePostId), note));
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
