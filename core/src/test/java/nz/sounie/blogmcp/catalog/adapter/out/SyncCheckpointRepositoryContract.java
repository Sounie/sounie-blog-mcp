package nz.sounie.blogmcp.catalog.adapter.out;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import nz.sounie.blogmcp.catalog.domain.site.TestSites;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpoint;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpointRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@link SyncCheckpointRepository} port's contract, run against the in-memory fake and the file
 * repository.
 */
abstract class SyncCheckpointRepositoryContract {

  protected static final Instant SEEN = Instant.parse("2026-03-01T10:15:30.123456789Z");
  protected static final Instant RECONCILED = Instant.parse("2026-03-02T00:00:00Z");

  protected SyncCheckpointRepository repository;

  /** A fresh, empty repository. */
  protected abstract SyncCheckpointRepository newRepository();

  @BeforeEach
  void createRepository() {
    repository = newRepository();
  }

  /** Compares field by field: {@link SyncCheckpoint} has identity equality. */
  protected static void assertSameCheckpoint(
      Optional<SyncCheckpoint> actual, SyncCheckpoint expected) {
    assertThat(actual).isPresent();
    assertThat(actual.orElseThrow()).usingRecursiveComparison().isEqualTo(expected);
  }

  protected static SyncCheckpoint checkpoint(Instant seen, Instant reconciled) {
    return SyncCheckpoint.restore(
        TestSites.SOUNIE_WP_ID, Optional.of(seen), Optional.of(reconciled));
  }

  @Test
  void finds_nothing_when_empty() {
    assertThat(repository.find(TestSites.SOUNIE_WP_ID)).isEmpty();
  }

  @Test
  void saves_and_finds_a_checkpoint_per_site() {
    SyncCheckpoint wp = checkpoint(SEEN, RECONCILED);
    SyncCheckpoint elegant = SyncCheckpoint.start(TestSites.ELEGANT_ID);

    repository.save(wp);
    repository.save(elegant);

    assertSameCheckpoint(repository.find(TestSites.SOUNIE_WP_ID), wp);
    assertSameCheckpoint(repository.find(TestSites.ELEGANT_ID), elegant);
  }

  @Test
  void save_replaces_the_stored_checkpoint() {
    repository.save(SyncCheckpoint.start(TestSites.SOUNIE_WP_ID));
    SyncCheckpoint later = checkpoint(SEEN, RECONCILED);

    repository.save(later);

    assertSameCheckpoint(repository.find(TestSites.SOUNIE_WP_ID), later);
  }

  @Test
  void delete_removes_the_checkpoint() {
    repository.save(checkpoint(SEEN, RECONCILED));

    repository.delete(TestSites.SOUNIE_WP_ID);
    repository.delete(TestSites.ELEGANT_ID);

    assertThat(repository.find(TestSites.SOUNIE_WP_ID)).isEmpty();
  }

  @Test
  @DisplayName("AC-APP-40: every find returns a fresh checkpoint")
  void every_find_returns_a_fresh_instance() {
    SyncCheckpoint saved = checkpoint(SEEN, RECONCILED);
    repository.save(saved);

    SyncCheckpoint first = repository.find(TestSites.SOUNIE_WP_ID).orElseThrow();
    SyncCheckpoint second = repository.find(TestSites.SOUNIE_WP_ID).orElseThrow();

    assertThat(first).isNotSameAs(second).isNotSameAs(saved);
  }

  @Test
  @DisplayName("AC-APP-40: advancing a found checkpoint changes nothing stored until it is saved")
  void unsaved_advance_is_not_visible() {
    SyncCheckpoint saved = checkpoint(SEEN, RECONCILED);
    repository.save(saved);
    SyncCheckpoint found = repository.find(TestSites.SOUNIE_WP_ID).orElseThrow();

    found.advanceTo(SEEN.plusSeconds(60));

    assertSameCheckpoint(repository.find(TestSites.SOUNIE_WP_ID), checkpoint(SEEN, RECONCILED));
  }

  @Test
  @DisplayName("AC-APP-40: after save, a new find returns the advanced checkpoint")
  void saved_advance_is_visible() {
    repository.save(checkpoint(SEEN, RECONCILED));
    SyncCheckpoint found = repository.find(TestSites.SOUNIE_WP_ID).orElseThrow();
    found.advanceTo(SEEN.plusSeconds(60));

    repository.save(found);

    assertSameCheckpoint(
        repository.find(TestSites.SOUNIE_WP_ID), checkpoint(SEEN.plusSeconds(60), RECONCILED));
  }
}
