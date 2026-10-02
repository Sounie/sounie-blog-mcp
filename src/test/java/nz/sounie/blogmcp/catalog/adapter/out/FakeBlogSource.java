package nz.sounie.blogmcp.catalog.adapter.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import nz.sounie.blogmcp.catalog.application.BlogSource;
import nz.sounie.blogmcp.catalog.domain.site.Site;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;
import nz.sounie.blogmcp.catalog.domain.sync.ChangeOrder;
import nz.sounie.blogmcp.catalog.domain.sync.PageCursor;
import nz.sounie.blogmcp.catalog.domain.sync.SourceEntry;
import nz.sounie.blogmcp.catalog.domain.sync.SourcePage;
import nz.sounie.blogmcp.catalog.domain.sync.SourceUnavailable;

/**
 * Scriptable fake {@link BlogSource}. Each site is given a list of steps; the cursor is the 1-based
 * step number. A step is either a page of entries or a failure. A site with no script serves one
 * empty page.
 */
public final class FakeBlogSource implements BlogSource {

  /** One call to {@link #fetch}. */
  public record FetchRequest(SiteId siteId, Optional<Instant> changedSince, PageCursor cursor) {}

  /** One scripted response. */
  public sealed interface Step {}

  /** A page of entries. */
  public record Page(List<SourceEntry> entries) implements Step {}

  /** A transport or HTTP failure. */
  public record Failure(String message) implements Step {}

  public static Step page(SourceEntry... entries) {
    return new Page(List.of(entries));
  }

  public static Step failure(String message) {
    return new Failure(message);
  }

  private final ChangeOrder changeOrder;
  private final Map<SiteId, List<Step>> scripts = new ConcurrentHashMap<>();
  private final List<FetchRequest> requests = new CopyOnWriteArrayList<>();
  private volatile Gate gate;

  public FakeBlogSource(ChangeOrder changeOrder) {
    this.changeOrder = changeOrder;
  }

  /** Replaces the script for a site. */
  public void willServe(SiteId siteId, Step... steps) {
    scripts.put(siteId, List.of(steps));
  }

  /** Makes the next fetch block until the returned gate is released. */
  public Gate holdNextFetch() {
    Gate newGate = new Gate();
    gate = newGate;
    return newGate;
  }

  public List<FetchRequest> requests() {
    return List.copyOf(requests);
  }

  public List<FetchRequest> requestsFor(SiteId siteId) {
    return requests.stream().filter(r -> r.siteId().equals(siteId)).toList();
  }

  @Override
  public ChangeOrder changeOrder() {
    return changeOrder;
  }

  @Override
  public SourcePage fetch(Site site, Optional<Instant> changedSince, PageCursor cursor) {
    requests.add(new FetchRequest(site.id(), changedSince, cursor));
    Gate current = gate;
    if (current != null) {
      gate = null;
      current.hold();
    }
    List<Step> steps = scripts.getOrDefault(site.id(), List.of(new Page(List.of())));
    int index = cursor.value() - 1;
    if (index < 0 || index >= steps.size()) {
      throw new AssertionError("Fake source has no page at cursor " + cursor.value());
    }
    Optional<PageCursor> next =
        index + 1 < steps.size()
            ? Optional.of(new PageCursor(cursor.value() + 1))
            : Optional.empty();
    return switch (steps.get(index)) {
      case Page page -> new SourcePage(page.entries(), next);
      case Failure failure -> throw new SourceUnavailable(failure.message());
    };
  }

  /** Blocks one fetch until released, so a test can act while a sync is in progress. */
  public static final class Gate {
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    private void hold() {
      started.countDown();
      try {
        if (!released.await(10, TimeUnit.SECONDS)) {
          throw new AssertionError("Gate was never released");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }

    /** Whether the held fetch has started, waiting up to the timeout. */
    public boolean awaitStarted(Duration timeout) throws InterruptedException {
      return started.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    public void release() {
      released.countDown();
    }
  }
}
