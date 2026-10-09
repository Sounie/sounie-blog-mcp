package nz.sounie.blogmcp.app;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Process-wide ownership lock for a data directory shared by application instances. */
final class DataDirectoryLock implements AutoCloseable {

  private final FileChannel channel;
  private final FileLock lock;

  private DataDirectoryLock(FileChannel channel, FileLock lock) {
    this.channel = channel;
    this.lock = lock;
  }

  static DataDirectoryLock acquire(Path dataDirectory) {
    Path lockFile = dataDirectory.resolve(".blog-mcp.lock");
    FileChannel channel = openChannel(lockFile, dataDirectory);
    FileLock lock = lockChannel(channel, dataDirectory);
    return new DataDirectoryLock(channel, lock);
  }

  private static FileChannel openChannel(Path lockFile, Path dataDirectory) {
    try {
      return FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
    } catch (IOException e) {
      throw new StartupFailure("Cannot lock the data directory " + dataDirectory + ": " + e);
    }
  }

  private static FileLock lockChannel(FileChannel channel, Path dataDirectory) {
    try {
      return requireLock(requestFileLock(channel, dataDirectory), dataDirectory);
    } catch (RuntimeException failure) {
      closeAfterFailure(channel, failure);
      throw failure;
    }
  }

  private static FileLock requestFileLock(FileChannel channel, Path dataDirectory) {
    try {
      return channel.tryLock();
    } catch (OverlappingFileLockException e) {
      throw new StartupFailure("The data directory is already in use: " + dataDirectory);
    } catch (IOException e) {
      throw new StartupFailure("Cannot lock the data directory " + dataDirectory + ": " + e);
    }
  }

  private static FileLock requireLock(FileLock lock, Path dataDirectory) {
    if (lock == null) {
      throw new StartupFailure("The data directory is already in use: " + dataDirectory);
    }
    return lock;
  }

  private static void closeAfterFailure(FileChannel channel, Exception failure) {
    try {
      channel.close();
    } catch (IOException closeFailure) {
      failure.addSuppressed(closeFailure);
    }
  }

  void assertHeld() {
    if (!lock.isValid()) {
      throw new IllegalStateException("The data-directory lock is not held");
    }
  }

  @Override
  public void close() {
    try {
      lock.release();
      channel.close();
    } catch (IOException e) {
      throw new IllegalStateException("Cannot release the data-directory lock", e);
    }
  }
}
