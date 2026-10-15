package nz.sounie.blogmcp.search.application;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.image.ImageRejection;
import nz.sounie.blogmcp.search.domain.query.SearchResults;

/**
 * The outcome of finding posts like an image (search.md 3.8). An embedder failure is not an
 * outcome: it stays {@code EmbedderUnavailable}, as for text search.
 */
public sealed interface ImageSearch {

  /** The posts in scope, ranked against the image's embedding. */
  record Ranked(SearchResults results) implements ImageSearch {
    public Ranked {
      Objects.requireNonNull(results, "results");
    }
  }

  /** The image can never be used, for this reason. */
  record ImageRefused(ImageRejection reason) implements ImageSearch {
    public ImageRefused {
      Objects.requireNonNull(reason, "reason");
    }
  }

  /** The image could not be loaded this time; the detail says why. */
  record ImageUnavailable(String detail) implements ImageSearch {
    public ImageUnavailable {
      Objects.requireNonNull(detail, "detail");
    }
  }
}
