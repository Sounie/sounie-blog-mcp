package nz.sounie.blogmcp.search.adapter.out;

import nz.sounie.blogmcp.search.domain.index.IndexedPost;

/** What reading one index file found. */
sealed interface IndexFileLoad {

  /** A readable file built with the current model. */
  record Loaded(IndexedPost post) implements IndexFileLoad {}

  /** A readable file built with another model: its vectors are in a different space. */
  record IncompatibleModel(String storedModelId) implements IndexFileLoad {}

  /** Not JSON, truncated, an unknown format, or a value that breaks a domain rule. */
  record Unreadable(String reason) implements IndexFileLoad {}
}
