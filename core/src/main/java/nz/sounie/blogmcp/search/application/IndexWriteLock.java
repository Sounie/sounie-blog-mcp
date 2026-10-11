package nz.sounie.blogmcp.search.application;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/** The single lock that serialises index mutations ({@link IndexPost}, {@link ReconcileIndex}). */
public final class IndexWriteLock {

  private final ReentrantLock lock = new ReentrantLock();

  public <T> T locked(Supplier<T> mutation) {
    lock.lock();
    try {
      return mutation.get();
    } finally {
      lock.unlock();
    }
  }
}
