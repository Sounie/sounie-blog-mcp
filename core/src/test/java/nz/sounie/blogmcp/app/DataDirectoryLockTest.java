package nz.sounie.blogmcp.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataDirectoryLockTest {

  @TempDir Path dataDirectory;

  @Test
  @DisplayName("AC-APP-47: only one app instance can own a shared data directory")
  void prevents_concurrent_owners_and_releases_the_lock() {
    DataDirectoryLock lock = DataDirectoryLock.acquire(dataDirectory);
    lock.assertHeld();

    assertThatThrownBy(() -> DataDirectoryLock.acquire(dataDirectory))
        .isInstanceOf(StartupFailure.class)
        .hasMessageContaining("already in use");

    lock.close();
    try (DataDirectoryLock nextOwner = DataDirectoryLock.acquire(dataDirectory)) {
      nextOwner.assertHeld();
      assertThat(dataDirectory.resolve(".blog-mcp.lock")).isRegularFile();
    }
  }
}
