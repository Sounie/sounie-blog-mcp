package nz.sounie.blogmcp.search.application;

import java.util.function.Supplier;

/** The single lock that serialises index mutations ({@link IndexPost}, {@link ReconcileIndex}). */
public final class IndexWriteLock {

  public <T> T locked(Supplier<T> mutation) {
    throw new UnsupportedOperationException("not implemented");
  }
}
