package nz.sounie.blogmcp.catalog.adapter.out;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import nz.sounie.blogmcp.catalog.domain.site.SiteId;
import nz.sounie.blogmcp.catalog.domain.site.TestSites;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpoint;
import nz.sounie.blogmcp.catalog.domain.sync.SyncCheckpointRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Real files in a temporary directory, real Jackson 3, real file system. */
class FileSyncCheckpointRepositoryTest extends SyncCheckpointRepositoryContract {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final SiteId THIRD = new SiteId("third");
  private static final SiteId FOURTH = new SiteId("fourth");

  @TempDir Path dataDirectory;

  private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
  private final PrintStream errors = new PrintStream(errorBytes, true, UTF_8);

  @Override
  protected SyncCheckpointRepository newRepository() {
    return FileSyncCheckpointRepository.open(dataDirectory, errors);
  }

  private FileSyncCheckpointRepository reopen() {
    return FileSyncCheckpointRepository.open(dataDirectory, errors);
  }

  private Path fileOf(SiteId siteId) {
    return dataDirectory
        .resolve("catalog")
        .resolve("checkpoints")
        .resolve(siteId.value() + ".json");
  }

  private List<String> errorLines() {
    return errorBytes.toString(UTF_8).lines().toList();
  }

  @Test
  @DisplayName("AC-APP-24: checkpoints with and without each optional instant survive a restart")
  void checkpoints_survive_a_restart() {
    List<SyncCheckpoint> checkpoints =
        List.of(
            checkpoint(SEEN, RECONCILED),
            SyncCheckpoint.restore(TestSites.ELEGANT_ID, Optional.of(SEEN), Optional.empty()),
            SyncCheckpoint.restore(THIRD, Optional.empty(), Optional.of(RECONCILED)),
            SyncCheckpoint.start(FOURTH));
    checkpoints.forEach(repository::save);

    FileSyncCheckpointRepository reopened = reopen();

    checkpoints.forEach(saved -> assertSameCheckpoint(reopened.find(saved.siteId()), saved));
    assertThat(fileOf(TestSites.SOUNIE_WP_ID)).isRegularFile();
    assertThat(errorLines()).isEmpty();
  }

  @Test
  @DisplayName("AC-APP-24: delete removes the file, and the deletion survives a restart")
  void deletion_survives_a_restart() {
    repository.save(checkpoint(SEEN, RECONCILED));
    repository.save(SyncCheckpoint.start(TestSites.ELEGANT_ID));

    repository.delete(TestSites.SOUNIE_WP_ID);

    assertThat(fileOf(TestSites.SOUNIE_WP_ID)).doesNotExist();
    FileSyncCheckpointRepository reopened = reopen();
    assertThat(reopened.find(TestSites.SOUNIE_WP_ID)).isEmpty();
    assertThat(reopened.find(TestSites.ELEGANT_ID)).isPresent();
  }

  @Test
  @DisplayName("AC-APP-26: a leftover partial temporary file is deleted and never read")
  void leftover_temporary_file_is_deleted_and_ignored() throws IOException {
    SyncCheckpoint saved = checkpoint(SEEN, RECONCILED);
    repository.save(saved);
    Path leftover = fileOf(TestSites.SOUNIE_WP_ID).resolveSibling("sounie-wp.json.tmp");
    Files.writeString(leftover, "{\"siteId\":\"sounie-wp\",\"changesSeen");

    FileSyncCheckpointRepository reopened = reopen();

    assertSameCheckpoint(reopened.find(TestSites.SOUNIE_WP_ID), saved);
    assertThat(leftover).doesNotExist();
    assertThat(errorLines()).isEmpty();
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {"truncated", "not JSON", "invalid instant", "missing format", "unknown format"})
  @DisplayName("AC-APP-27: a corrupt checkpoint is quarantined, logged and treated as absent")
  void corrupt_checkpoint_is_quarantined_and_absent(String kind) throws IOException {
    SyncCheckpoint healthy = SyncCheckpoint.start(TestSites.ELEGANT_ID);
    repository.save(checkpoint(SEEN, RECONCILED));
    repository.save(healthy);
    Path file = fileOf(TestSites.SOUNIE_WP_ID);
    byte[] valid = Files.readAllBytes(file);
    Files.write(file, corrupted(kind, valid));

    FileSyncCheckpointRepository reopened = reopen();

    assertThat(reopened.find(TestSites.SOUNIE_WP_ID)).isEmpty();
    assertSameCheckpoint(reopened.find(TestSites.ELEGANT_ID), healthy);
    assertThat(file).doesNotExist();
    assertThat(file.resolveSibling("sounie-wp.json.corrupt")).isRegularFile();
    assertThat(errorLines()).singleElement().asString().contains(file.toString());
  }

  private static byte[] corrupted(String kind, byte[] valid) {
    return switch (kind) {
      case "truncated" -> Arrays.copyOf(valid, valid.length / 2);
      case "not JSON" -> "<checkpoint/>".getBytes(UTF_8);
      case "missing format" -> {
        ObjectNode json = (ObjectNode) JSON.readTree(valid);
        json.remove("format");
        yield JSON.writeValueAsBytes(json);
      }
      case "unknown format" -> {
        ObjectNode json = (ObjectNode) JSON.readTree(valid);
        json.put("format", 99);
        yield JSON.writeValueAsBytes(json);
      }
      default -> {
        ObjectNode json = (ObjectNode) JSON.readTree(valid);
        json.put("changesSeenUpTo", "yesterday-ish");
        yield JSON.writeValueAsBytes(json);
      }
    };
  }

  @Test
  @DisplayName("S4: a saved checkpoint file carries \"format\": 1")
  void saved_checkpoint_file_carries_its_format() throws IOException {
    repository.save(checkpoint(SEEN, RECONCILED));

    ObjectNode json =
        (ObjectNode) JSON.readTree(Files.readAllBytes(fileOf(TestSites.SOUNIE_WP_ID)));

    assertThat(json.has("format")).isTrue();
    assertThat(json.get("format").isIntegralNumber()).isTrue();
    assertThat(json.get("format").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("S3: a checkpoint file found at another site's location is quarantined")
  void checkpoint_file_at_another_sites_location_is_quarantined() throws IOException {
    repository.save(SyncCheckpoint.start(TestSites.ELEGANT_ID));
    Path pathOfWp = fileOf(TestSites.SOUNIE_WP_ID);
    Files.move(fileOf(TestSites.ELEGANT_ID), pathOfWp);

    FileSyncCheckpointRepository reopened = reopen();

    assertThat(reopened.find(TestSites.SOUNIE_WP_ID)).isEmpty();
    assertThat(reopened.find(TestSites.ELEGANT_ID)).isEmpty();
    assertThat(pathOfWp).doesNotExist();
    assertThat(pathOfWp.resolveSibling("sounie-wp.json.corrupt")).isRegularFile();
    assertThat(errorLines()).singleElement().asString().contains(pathOfWp.toString());
  }

  @Test
  @DisplayName("S3: a deleted checkpoint never comes back from a file at another site's location")
  void deleted_checkpoint_is_not_resurrected_by_a_misplaced_file() throws IOException {
    repository.save(SyncCheckpoint.start(TestSites.ELEGANT_ID));
    Files.move(fileOf(TestSites.ELEGANT_ID), fileOf(TestSites.SOUNIE_WP_ID));
    FileSyncCheckpointRepository opened = reopen();

    opened.delete(TestSites.ELEGANT_ID);

    assertThat(reopen().find(TestSites.ELEGANT_ID)).isEmpty();
  }

  @Test
  @DisplayName(
      "ADR 0007: a checkpoint file that cannot be read from disk is quarantined; startup continues")
  void checkpoint_file_unreadable_from_disk_is_quarantined() throws IOException {
    SyncCheckpoint healthy = SyncCheckpoint.start(TestSites.ELEGANT_ID);
    repository.save(checkpoint(SEEN, RECONCILED));
    repository.save(healthy);
    Path file = fileOf(TestSites.SOUNIE_WP_ID);
    Files.setPosixFilePermissions(file, Set.of());
    assumeFalse(Files.isReadable(file), "the file is still readable (running as root?)");

    FileSyncCheckpointRepository reopened = reopen();

    assertThat(reopened.find(TestSites.SOUNIE_WP_ID)).isEmpty();
    assertSameCheckpoint(reopened.find(TestSites.ELEGANT_ID), healthy);
    assertThat(file.resolveSibling("sounie-wp.json.corrupt")).exists();
    assertThat(errorLines()).singleElement().asString().contains(file.toString());
  }

  @Test
  @DisplayName("AC-APP-40: an unsaved advance is not visible after a restart; a saved one is")
  void only_saved_advances_survive_a_restart() {
    repository.save(checkpoint(SEEN, RECONCILED));
    SyncCheckpoint found = repository.find(TestSites.SOUNIE_WP_ID).orElseThrow();
    found.advanceTo(SEEN.plusSeconds(60));

    assertSameCheckpoint(reopen().find(TestSites.SOUNIE_WP_ID), checkpoint(SEEN, RECONCILED));

    repository.save(found);

    assertSameCheckpoint(
        reopen().find(TestSites.SOUNIE_WP_ID), checkpoint(SEEN.plusSeconds(60), RECONCILED));
  }
}
