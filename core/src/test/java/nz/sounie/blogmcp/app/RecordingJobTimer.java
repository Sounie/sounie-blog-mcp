package nz.sounie.blogmcp.app;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Fake of our {@link JobTimer} port: records each request and runs nothing by itself. */
final class RecordingJobTimer implements JobTimer {

  record Request(Duration delay, Runnable job) {}

  private final List<Request> requests = new CopyOnWriteArrayList<>();

  @Override
  public void runNowThenEvery(Duration delay, Runnable job) {
    requests.add(new Request(delay, job));
  }

  List<Request> requests() {
    return List.copyOf(requests);
  }
}
