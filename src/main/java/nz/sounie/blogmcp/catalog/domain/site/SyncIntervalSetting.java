package nz.sounie.blogmcp.catalog.domain.site;

import java.util.Objects;
import java.util.Optional;

/**
 * What the sites configuration says about the sync interval ({@code syncEveryHours}), before
 * validation. Each variant contributes zero or one violation, and gives the interval once valid.
 */
public sealed interface SyncIntervalSetting {

  /** Zero or one violation of the interval rule: a whole number of hours, at least 1. */
  Optional<SitesConfigurationViolation> violation();

  /**
   * The interval. Only called once {@link #violation()} is empty.
   *
   * @throws IllegalStateException for a setting that is a violation
   */
  SyncInterval toInterval();

  /** No {@code syncEveryHours} in the file: the default applies. */
  record Omitted() implements SyncIntervalSetting {
    @Override
    public Optional<SitesConfigurationViolation> violation() {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.8)");
    }

    @Override
    public SyncInterval toInterval() {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.8)");
    }
  }

  /** An integral number, valid when at least 1. */
  record WholeHours(long hours) implements SyncIntervalSetting {
    @Override
    public Optional<SitesConfigurationViolation> violation() {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.8)");
    }

    @Override
    public SyncInterval toInterval() {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.8)");
    }
  }

  /** Anything else (a string, a fraction, a boolean, null, an object), as written. */
  record Unparseable(String text) implements SyncIntervalSetting {
    public Unparseable {
      Objects.requireNonNull(text, "text");
    }

    @Override
    public Optional<SitesConfigurationViolation> violation() {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.8)");
    }

    @Override
    public SyncInterval toInterval() {
      throw new UnsupportedOperationException("not implemented yet (app.md 3.8)");
    }
  }
}
