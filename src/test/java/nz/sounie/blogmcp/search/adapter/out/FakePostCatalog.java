package nz.sounie.blogmcp.search.adapter.out;

import java.util.Arrays;
import java.util.List;
import nz.sounie.blogmcp.search.application.PostCatalog;
import nz.sounie.blogmcp.search.domain.CatalogEntry;
import nz.sounie.blogmcp.search.domain.PostToIndex;

/** Fake of our {@link PostCatalog} port: the catalog's current entries, set by the test. */
public final class FakePostCatalog implements PostCatalog {

  private volatile List<CatalogEntry> entries = List.of();

  /** Readable posts only. */
  public FakePostCatalog holding(PostToIndex... current) {
    this.entries = Arrays.stream(current).<CatalogEntry>map(CatalogEntry.Readable::new).toList();
    return this;
  }

  /** Any mix of readable, unreadable and unidentified entries. */
  public FakePostCatalog holdingEntries(CatalogEntry... current) {
    this.entries = List.of(current);
    return this;
  }

  @Override
  public List<CatalogEntry> currentPosts() {
    return List.copyOf(entries);
  }
}
