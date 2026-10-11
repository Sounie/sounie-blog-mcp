package nz.sounie.blogmcp.app;

import java.time.Duration;

/** Port: runs a job now, then repeatedly with a fixed delay between runs. Runs never overlap. */
public interface JobTimer {

  /**
   * @param delay the time between the end of one run and the start of the next
   */
  void runNowThenEvery(Duration delay, Runnable job);
}
