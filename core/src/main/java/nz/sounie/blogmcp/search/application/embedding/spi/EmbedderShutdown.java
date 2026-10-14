package nz.sounie.blogmcp.search.application.embedding.spi;

/** The outcome of a provider shutdown (search glossary: "Provider shutdown"). */
public enum EmbedderShutdown {
  /** No model call is running, and none will start. */
  DRAINED,
  /** The running model call did not finish within the wait; no new call will start. */
  TIMED_OUT
}
