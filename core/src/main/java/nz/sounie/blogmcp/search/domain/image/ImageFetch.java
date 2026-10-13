package nz.sounie.blogmcp.search.domain.image;

import java.util.Objects;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;

/** The outcome of fetching one post image. Expected outcomes are values, never exceptions. */
public sealed interface ImageFetch {

  /** The decoded pixels. */
  record Fetched(ImagePixels pixels) implements ImageFetch {
    public Fetched {
      Objects.requireNonNull(pixels, "pixels");
    }
  }

  /** The image itself can never be indexed: retrying is pointless. */
  record Rejected(ImageRejection reason) implements ImageFetch {
    public Rejected {
      Objects.requireNonNull(reason, "reason");
    }
  }

  /** A transient failure (timeout, connection error, server error): worth retrying. */
  record Unavailable(String detail) implements ImageFetch {
    public Unavailable {
      Objects.requireNonNull(detail, "detail");
    }
  }
}
