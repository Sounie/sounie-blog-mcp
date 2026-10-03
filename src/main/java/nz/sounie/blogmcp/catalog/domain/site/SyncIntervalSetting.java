package nz.sounie.blogmcp.catalog.domain.site;

import java.util.Objects;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.SitesConfigurationViolation.Kind;

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
      return Optional.empty();
    }

    @Override
    public SyncInterval toInterval() {
      return SyncInterval.DEFAULT;
    }
  }

  /** An integral number, valid when at least 1. */
  record WholeHours(long hours) implements SyncIntervalSetting {
    @Override
    public Optional<SitesConfigurationViolation> violation() {
      return Optional.of(hours)
          .filter(value -> value < 1)
          .map(
              value ->
                  new SitesConfigurationViolation(
                      Kind.INVALID_SYNC_INTERVAL,
                      "syncEveryHours must be at least 1, got " + value));
    }

    @Override
    public SyncInterval toInterval() {
      return SyncInterval.ofHours(hours);
    }
  }

  /** Anything else (a string, a fraction, a boolean, null, an object), as written. */
  record Unparseable(String text) implements SyncIntervalSetting {
    public Unparseable {
      Objects.requireNonNull(text, "text");
    }

    @Override
    public Optional<SitesConfigurationViolation> violation() {
      return Optional.of(
          new SitesConfigurationViolation(
              Kind.INVALID_SYNC_INTERVAL,
              "syncEveryHours must be a whole number of hours, at least 1, got " + text));
    }

    @Override
    public SyncInterval toInterval() {
      throw new IllegalStateException("syncEveryHours is not a whole number of hours: " + text);
    }
  }
}
