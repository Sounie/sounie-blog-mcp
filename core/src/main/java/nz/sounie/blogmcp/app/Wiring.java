package nz.sounie.blogmcp.app;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;
import nz.sounie.blogmcp.catalog.adapter.out.FilePostRepository;
import nz.sounie.blogmcp.catalog.adapter.out.FileSyncCheckpointRepository;
import nz.sounie.blogmcp.catalog.adapter.out.StorageHealth;
import nz.sounie.blogmcp.catalog.application.GetPost;
import nz.sounie.blogmcp.catalog.application.ListCatalogPosts;
import nz.sounie.blogmcp.catalog.application.SiteDirectory;
import nz.sounie.blogmcp.catalog.application.SyncAllSites;
import nz.sounie.blogmcp.catalog.application.SyncReport;
import nz.sounie.blogmcp.catalog.application.SyncSite;
import nz.sounie.blogmcp.catalog.domain.site.SyncInterval;
import nz.sounie.blogmcp.search.adapter.in.CatalogEventListener;
import nz.sounie.blogmcp.search.adapter.out.FileVectorIndex;
import nz.sounie.blogmcp.search.adapter.out.HttpImageSource;
import nz.sounie.blogmcp.search.adapter.out.QueryImageLoader;
import nz.sounie.blogmcp.search.adapter.out.SharedCatalogPosts;
import nz.sounie.blogmcp.search.application.FindPostsLikeImage;
import nz.sounie.blogmcp.search.application.IndexPost;
import nz.sounie.blogmcp.search.application.IndexWriteLock;
import nz.sounie.blogmcp.search.application.ReconcileIndex;
import nz.sounie.blogmcp.search.application.ReconcileReport;
import nz.sounie.blogmcp.search.application.SearchPosts;
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.image.ImageSource;
import nz.sounie.blogmcp.search.domain.index.ImageIndexing;
import nz.sounie.blogmcp.search.domain.index.PostIndexer;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.text.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.text.PassageComposition;
import nz.sounie.blogmcp.shared.event.InProcessEventBus;

/**
 * Constructs everything: the file repositories on the data directory, the catalog and search use
 * cases, the in-process event bus with search's listener, and the sync-and-reconcile job.
 */
public final class Wiring {

  private final SearchPosts searchPosts;
  private final Optional<FindPostsLikeImage> findPostsLikeImage;
  private final GetPost getPost;
  private final StorageHealth storageHealth;
  private final SyncAndReconcile job;

  private Wiring(
      SearchPosts searchPosts,
      Optional<FindPostsLikeImage> findPostsLikeImage,
      GetPost getPost,
      StorageHealth storageHealth,
      SyncAndReconcile job) {
    this.searchPosts = searchPosts;
    this.findPostsLikeImage = findPostsLikeImage;
    this.getPost = getPost;
    this.storageHealth = storageHealth;
    this.job = job;
  }

  public static Wiring assemble(
      Path dataDirectory, SiteDirectory sites, Adapters adapters, PrintStream errors) {
    FilePostRepository posts = FilePostRepository.open(dataDirectory, errors);
    FileSyncCheckpointRepository checkpoints =
        FileSyncCheckpointRepository.open(dataDirectory, errors);
    var budget = adapters.profile().passageTokenBudget();
    ChunkingPolicy chunking =
        new ChunkingPolicy(
            ChunkingPolicy.TARGET_WORDS,
            budget.bodyTokens(),
            ChunkingPolicy.OVERLAP_WORDS,
            budget.overlapTokens());
    PassageComposition composition =
        new PassageComposition(budget.titleTokens(), PassageComposition.VERSION);
    Optional<ImageCapability> images =
        adapters
            .imageEmbedder()
            .map(embedder -> new ImageCapability(embedder, new HttpImageSource(errors)));
    PostIndexer indexer =
        new PostIndexer(
                chunking,
                composition,
                adapters.tokenCounter(),
                adapters.passageEmbedder(),
                adapters.passageFormatter(),
                adapters.passagePreparationVersion(),
                budget)
            .withImageIndexing(
                images
                    .map(image -> image.indexing(adapters.postImages()))
                    .orElseGet(ImageIndexing::textOnly));
    FileVectorIndex index =
        FileVectorIndex.open(
            dataDirectory,
            adapters.profile().indexProfileKey(),
            adapters.passageEmbedder().modelId(),
            indexer.recipe(),
            errors);
    IndexWriteLock lock = new IndexWriteLock();

    InProcessEventBus bus = new InProcessEventBus();
    new CatalogEventListener(new IndexPost(index, indexer, lock), errors).subscribeTo(bus);

    SyncAllSites syncAllSites =
        new SyncAllSites(
            sites,
            new SyncSite(sites, adapters.sources(), posts, checkpoints, bus, adapters.clock()),
            posts,
            checkpoints,
            bus,
            adapters.clock());
    ReconcileIndex reconcile =
        new ReconcileIndex(
            new SharedCatalogPosts(new ListCatalogPosts(posts)), index, indexer, lock);
    RunLog log = new RunLog(errors);
    SyncAndReconcile job =
        new SyncAndReconcile(
            mode -> syncAllSites.run(mode).forEach(log::synced),
            () -> log.reconciled(reconcile.run()),
            posts.health().startupSyncMode(),
            errors);

    return new Wiring(
        new SearchPosts(index, adapters.queryEmbedder(), adapters.queryPreparation()),
        images.map(image -> image.findPostsLikeImage(index)),
        new GetPost(posts),
        posts.health(),
        job);
  }

  public SearchPosts searchPosts() {
    return searchPosts;
  }

  /**
   * Search by image, built only when the provider can embed images (search.md 3.8); empty for a
   * text-only distribution, which then offers no image tool (app.md 3.9).
   */
  public Optional<FindPostsLikeImage> findPostsLikeImage() {
    return findPostsLikeImage;
  }

  public GetPost getPost() {
    return getPost;
  }

  /** What loading the stored posts found. */
  public StorageHealth storageHealth() {
    return storageHealth;
  }

  /** The sync-and-reconcile job; its first run uses {@code storageHealth().startupSyncMode()}. */
  public SyncAndReconcile job() {
    return job;
  }

  /** Asks the timer to run the job now and then with the interval as a fixed delay. */
  public void schedule(JobTimer timer, SyncInterval interval) {
    timer.runNowThenEvery(interval.value(), job);
  }

  /**
   * What an image-capable provider adds (search.md 3.7 and 3.8): its image embedder and one HTTP
   * image source, shared by post images (unless the adapters supply another source) and query URLs.
   * A text-only distribution never constructs one.
   */
  private record ImageCapability(ImageEmbedder embedder, HttpImageSource http) {

    ImageIndexing indexing(Function<HttpImageSource, ImageSource> postImages) {
      return ImageIndexing.withImages(postImages.apply(http), embedder);
    }

    FindPostsLikeImage findPostsLikeImage(VectorIndex index) {
      return new FindPostsLikeImage(index, embedder, new QueryImageLoader(http));
    }
  }

  /** One stderr line per site synced, and one per post the reconcile could not index. */
  private record RunLog(PrintStream errors) {

    void synced(SyncReport report) {
      errors.println(
          "catalog: "
              + report.mode()
              + " sync of "
              + report.siteId().value()
              + " "
              + report.outcome()
              + report.error().map(error -> ": " + error).orElse(""));
    }

    void reconciled(ReconcileReport report) {
      report
          .failureReasons()
          .forEach(
              (postId, reason) ->
                  errors.println("search: could not index " + postId.external() + ": " + reason));
    }
  }
}
