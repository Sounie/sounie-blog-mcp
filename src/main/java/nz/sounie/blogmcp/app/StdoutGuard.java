package nz.sounie.blogmcp.app;

import java.io.PrintStream;

/** Keeps stdout for the protocol only. */
public final class StdoutGuard {

  private StdoutGuard() {}

  /**
   * Captures the current {@code System.out} for the transport and points {@code System.out} at
   * {@code System.err}, so a stray {@code println} can never corrupt the protocol.
   *
   * @return the captured stdout, for the transport only
   */
  public static PrintStream install() {
    throw new UnsupportedOperationException("not implemented yet (app.md 3.4)");
  }
}
