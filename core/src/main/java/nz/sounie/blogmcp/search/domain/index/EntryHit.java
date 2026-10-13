package nz.sounie.blogmcp.search.domain.index;

import java.util.Comparator;
import java.util.Objects;
import nz.sounie.blogmcp.search.domain.embedding.Similarity;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.post.PostMetadata;

/** The similarity of one index entry to the query. */
public record EntryHit(IndexEntry entry, Similarity similarity) {

  /** Similarity descending, then chunks before images, then the lower index. */
  public static final Comparator<EntryHit> BEST_FIRST =
      Comparator.comparing(EntryHit::similarity)
          .reversed()
          .thenComparing(hit -> hit.entry().kind())
          .thenComparingInt(hit -> hit.entry().index());

  public EntryHit {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(similarity, "similarity");
  }

  /** The post's match through this entry: its similarity as score, its snippet and image. */
  PostMatch toMatch(PostId id, PostMetadata metadata) {
    return new PostMatch(id, metadata, similarity, entry.snippet(), entry.matchedImage());
  }
}
