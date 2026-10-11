package nz.sounie.blogmcp.catalog.application;

import java.util.Objects;

/** Whether a complete reconcile may withdraw the posts it did not see listed. */
sealed interface WithdrawalDecision {

  /** The listing is trustworthy: withdraw every stored post that was not listed. */
  record Withdraw() implements WithdrawalDecision {}

  /** The listing could make a listed post look missing: withdraw nothing, and say why. */
  record Suppress(SyncWarning.Kind warning, String detail) implements WithdrawalDecision {

    public Suppress {
      Objects.requireNonNull(warning, "warning");
      Objects.requireNonNull(detail, "detail");
    }
  }
}
