package nz.sounie.blogmcp.app;

import java.io.PrintStream;
import java.nio.file.Path;
import nz.sounie.blogmcp.catalog.adapter.out.StorageHealth;
import nz.sounie.blogmcp.catalog.application.GetPost;
import nz.sounie.blogmcp.catalog.application.SiteDirectory;
import nz.sounie.blogmcp.catalog.domain.site.SyncInterval;
import nz.sounie.blogmcp.search.application.SearchPosts;

/**
 * Constructs everything: the file repositories on the data directory, the catalog and search use
 * cases, the in-process event bus with search's listener, and the sync-and-reconcile job.
 */
public final class Wiring {

  private Wiring() {}

  public static Wiring assemble(
      Path dataDirectory, SiteDirectory sites, Adapters adapters, PrintStream errors) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }

  public SearchPosts searchPosts() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }

  public GetPost getPost() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }

  /** What loading the stored posts found. */
  public StorageHealth storageHealth() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }

  /** The sync-and-reconcile job; its first run uses {@code storageHealth().startupSyncMode()}. */
  public SyncAndReconcile job() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }

  /** Asks the timer to run the job now and then with the interval as a fixed delay. */
  public void schedule(JobTimer timer, SyncInterval interval) {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }
}
