package nz.sounie.blogmcp.search.adapter.out;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.application.IndexWriteLock;
import nz.sounie.blogmcp.search.application.ReconcileIndex;
import nz.sounie.blogmcp.search.application.ReconcileReport;
import nz.sounie.blogmcp.search.application.SearchContext;
import nz.sounie.blogmcp.search.domain.index.IndexOutcome;
import nz.sounie.blogmcp.search.domain.index.IndexedImage;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.index.PostIndexer;
import nz.sounie.blogmcp.search.domain.index.PostToIndex;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.text.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.text.PassageComposition;
import nz.sounie.blogmcp.shared.storage.FileKey;
import nz.sounie.blogmcp.shared.storage.StoredFiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ObjectNode;

/** Real files in a temporary directory, real Jackson 3, real file system. */
class FileVectorIndexTest extends VectorIndexContract {

  private static final int DIMENSION = 384;
  private static final String MODEL = "model-r1";
  private static final String PROFILE_ONNX = "onnx-bge-small-en-v1.5-q-v1";
  private static final String PROFILE_GEMMA = "gemma2-914f7f89-v1";

  /** Recipe R1: the standard chunking, under model-r1. */
  private static final PostIndexer INDEXER_R1 =
      SearchContext.indexerWith(new FakeEmbedder(MODEL, DIMENSION));

  /** Recipe R2: the same model, with different chunking. */
  private static final PostIndexer INDEXER_R2 =
      new PostIndexer(
          new ChunkingPolicy(200, ChunkingPolicy.BODY_TOKEN_BUDGET, 40, 80),
          PassageComposition.standard(),
          FakeTokenCounter.perWord(1),
          new FakeEmbedder(MODEL, DIMENSION));

  @TempDir Path dataDirectory;

  private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
  private final PrintStream errors = new PrintStream(errorBytes, true, UTF_8);

  @Override
  protected VectorIndex newIndex() {
    return FileVectorIndex.open(dataDirectory, MODEL, INDEXER_R1.recipe(), errors);
  }

  private FileVectorIndex reopen() {
    return FileVectorIndex.open(dataDirectory, MODEL, INDEXER_R1.recipe(), errors);
  }

  private Path fileOf(PostId id) {
    return dataDirectory
        .resolve("search")
        .resolve("index")
        .resolve(id.siteId().value())
        .resolve(FileKey.of(id.external()).value() + ".json");
  }

  private Path legacyFileOf(PostId id) {
    return fileOf(id);
  }

  /** Review #4: outside {@code search/index}, which older binaries scan at any depth. */
  private Path fileOf(String profileKey, PostId id) {
    return dataDirectory
        .resolve("search")
        .resolve("indexes")
        .resolve(profileKey)
        .resolve(id.siteId().value())
        .resolve(FileKey.of(id.external()).value() + ".json");
  }

  private FileVectorIndex profileIndex(String profileKey) {
    return FileVectorIndex.open(dataDirectory, profileKey, MODEL, INDEXER_R1.recipe(), errors);
  }

  private List<String> errorLines() {
    return errorBytes.toString(UTF_8).lines().toList();
  }

  private static List<IndexedPost> variedEntries() {
    return List.of(
        indexed(
            aPost().id("sounie-wp:1").tags("Java", "gRPC").build(),
            fingerprint('a'),
            TestEmbeddings.irregular(1),
            TestEmbeddings.irregular(2),
            TestEmbeddings.irregular(3)),
        indexed(aPost().id("sounie-wp:2").build(), fingerprint('b')),
        indexed(
            aPost()
                .id("elegant:tag:blogger.com,1999:blog-1.post-9")
                .title("Kia ora — Māori, 日本語 and 🌏")
                .publishedAt(Instant.parse("2020-01-01T00:00:00.123456789Z"))
                .updatedAt(Instant.parse("2020-01-02T00:00:00Z"))
                .build(),
            fingerprint('c'),
            TestEmbeddings.irregular(4)));
  }

  @Test
  @DisplayName(
      "AC-APP-25: the index survives a restart bit-exactly, including a post with no chunks")
  void index_survives_a_restart_bit_exactly() {
    List<IndexedPost> entries = variedEntries();
    entries.forEach(index::save);

    FileVectorIndex reopened = reopen();

    assertThat(reopened.ids())
        .containsExactlyInAnyOrderElementsOf(entries.stream().map(IndexedPost::id).toList());
    entries.forEach(
        entry -> {
          IndexedPost loaded = reopened.find(entry.id()).orElseThrow();
          assertThat(loaded).usingRecursiveComparison().isEqualTo(entry);
          IntStream.range(0, entry.chunks().size())
              .forEach(
                  i ->
                      assertThat(TestEmbeddings.rawBits(loaded.chunks().get(i).embedding()))
                          .containsExactly(
                              TestEmbeddings.rawBits(entry.chunks().get(i).embedding())));
        });
    assertThat(reopened.all()).hasSize(3);
    assertThat(errorLines()).isEmpty();
  }

  @Test
  @DisplayName(
      "AC-SRCH-74: a post with images survives a restart bit-exactly, in a format-2 file with an"
          + " images array")
  void images_survive_a_restart_in_format_two() {
    IndexedPost base =
        indexed(
            aPost().id("sounie-wp:7").build(),
            fingerprint('d'),
            TestEmbeddings.irregular(5),
            TestEmbeddings.irregular(6));
    IndexedPost entry =
        IndexedPost.restore(
            base.id(),
            base.metadata(),
            base.fingerprint(),
            base.chunks(),
            List.of(
                new IndexedImage(
                    0,
                    URI.create("https://x.example/swan.jpg"),
                    "Black swan",
                    TestEmbeddings.irregular(7)),
                new IndexedImage(
                    2,
                    URI.create("https://x.example/b.png"),
                    "Grapes",
                    TestEmbeddings.irregular(8))));
    index.save(entry);

    IndexedPost loaded = reopen().find(entry.id()).orElseThrow();

    assertThat(loaded).usingRecursiveComparison().isEqualTo(entry);
    IntStream.range(0, entry.images().size())
        .forEach(
            i ->
                assertThat(TestEmbeddings.rawBits(loaded.images().get(i).embedding()))
                    .containsExactly(TestEmbeddings.rawBits(entry.images().get(i).embedding())));
    ObjectNode json = (ObjectNode) IndexFileJson.JSON.readTree(fileOf(entry.id()));
    assertThat(json.get("format").intValue()).isEqualTo(2);
    assertThat(json.get("images").size()).isEqualTo(2);
    assertThat(errorLines()).isEmpty();
  }

  @Test
  @DisplayName(
      "AC-SRCH-74: a format-1 file loads with no images, is not quarantined, and a text-only"
          + " reconcile keeps it")
  void a_format_one_file_still_loads() throws IOException {
    PostToIndex post = aPost().id("sounie-wp:8").title("Before phase 3").words(80).build();
    Path file = fileOf(post.id());
    Files.createDirectories(file.getParent());
    Files.write(
        file,
        IndexFileJson.bytes(
            IndexFileJson.of(INDEXER_R1.index(post), MODEL, INDEXER_R1.recipe().id())));

    FileVectorIndex reopened = reopen();

    assertThat(reopened.find(post.id()))
        .hasValueSatisfying(loaded -> assertThat(loaded.images()).isEmpty());
    assertThat(file).isRegularFile();
    assertThat(errorLines()).isEmpty();
    assertThat(reconcile(reopened, INDEXER_R1, post).outcomes())
        .isEqualTo(Map.of(post.id(), IndexOutcome.UNCHANGED));
  }

  @Test
  @DisplayName("AC-APP-25: one file per post at search/index/<site>/<file key of the post ID>.json")
  void stores_one_file_per_post() {
    variedEntries().forEach(index::save);

    assertThat(dataDirectory.resolve("search/index/sounie-wp/sounie-wp%3a1.json")).isRegularFile();
    variedEntries().forEach(entry -> assertThat(fileOf(entry.id())).isRegularFile());
  }

  @Test
  @DisplayName("AC-APP-25: remove deletes the file, and the removal survives a restart")
  void removal_survives_a_restart() {
    IndexedPost kept = variedEntries().get(0);
    IndexedPost removed = variedEntries().get(1);
    index.save(kept);
    index.save(removed);

    index.remove(removed.id());

    assertThat(fileOf(removed.id())).doesNotExist();
    assertThat(reopen().ids()).containsExactly(kept.id());
  }

  @Test
  @DisplayName("AC-APP-26: every save moves its temporary file into place, leaving none behind")
  void saves_leave_no_temporary_files() throws IOException {
    index.save(first);
    index.save(second);

    assertThat(filesIn(fileOf(post.id()).getParent()))
        .containsExactly(fileOf(post.id()).getFileName().toString());
  }

  @Test
  @DisplayName("AC-APP-26: a leftover partial temporary file is deleted and never read")
  void leftover_temporary_file_is_deleted_and_ignored() throws IOException {
    index.save(first);
    Path leftover = fileOf(post.id()).resolveSibling(fileOf(post.id()).getFileName() + ".tmp");
    Files.writeString(leftover, "{\"format\":1,\"postId\":\"sounie-wp:1\",\"chunks\":[{\"ind");

    FileVectorIndex reopened = reopen();

    assertSameEntry(reopened.find(post.id()), first);
    assertThat(leftover).doesNotExist();
    assertThat(errorLines()).isEmpty();
  }

  @Test
  @DisplayName("AC-APP-28: a corrupt index file is quarantined; the reconcile re-adds the post")
  void corrupt_index_file_is_quarantined_and_re_added() throws IOException {
    PostToIndex damaged = aPost().id("sounie-wp:1").title("Damaged").words(50).build();
    PostToIndex intact = aPost().id("sounie-wp:2").title("Intact").words(50).build();
    index.save(INDEXER_R1.index(damaged));
    index.save(INDEXER_R1.index(intact));
    Path file = fileOf(damaged.id());
    ObjectNode json = (ObjectNode) IndexFileJson.JSON.readTree(Files.readAllBytes(file));
    IndexFileJson.chunk(json, 0).put("vector", "not*base64!");
    Files.write(file, IndexFileJson.bytes(json));

    FileVectorIndex reopened = reopen();

    assertThat(reopened.ids()).containsExactly(intact.id());
    assertThat(file).doesNotExist();
    assertThat(file.resolveSibling(file.getFileName() + ".corrupt")).isRegularFile();
    assertThat(errorLines()).singleElement().asString().contains(file.toString());

    ReconcileReport report = reconcile(reopened, INDEXER_R1, damaged, intact);

    assertThat(report.outcomes())
        .isEqualTo(Map.of(damaged.id(), IndexOutcome.ADDED, intact.id(), IndexOutcome.UNCHANGED));
    assertSameEntry(reopen().find(damaged.id()), INDEXER_R1.index(damaged));
  }

  @Test
  @DisplayName(
      "AC-APP-29: after a restart with a new recipe for the same model, entries are served, "
          + "then re-embedded and rewritten")
  void recipe_change_after_a_restart_re_embeds() throws IOException {
    PostToIndex a = aPost().id("sounie-wp:1").words(500).build();
    PostToIndex b = aPost().id("elegant:2").words(30).build();
    index.save(INDEXER_R1.index(a));
    index.save(INDEXER_R1.index(b));

    FileVectorIndex restarted =
        FileVectorIndex.open(dataDirectory, MODEL, INDEXER_R2.recipe(), errors);

    assertSameEntry(restarted.find(a.id()), INDEXER_R1.index(a));
    assertThat(restarted.ids()).containsExactlyInAnyOrder(a.id(), b.id());

    ReconcileReport report = reconcile(restarted, INDEXER_R2, a, b);

    assertThat(report.outcomes())
        .isEqualTo(Map.of(a.id(), IndexOutcome.RE_EMBEDDED, b.id(), IndexOutcome.RE_EMBEDDED));
    FileVectorIndex again = FileVectorIndex.open(dataDirectory, MODEL, INDEXER_R2.recipe(), errors);
    assertThat(again.find(a.id()).orElseThrow().fingerprint())
        .isEqualTo(INDEXER_R2.fingerprintOf(a));
    assertThat(storedField(fileOf(a.id()), "recipe")).isEqualTo(INDEXER_R2.recipe().id());
    assertThat(storedField(fileOf(a.id()), "modelId")).isEqualTo(MODEL);
    assertThat(errorLines()).isEmpty();
  }

  @Test
  @DisplayName(
      "AC-APP-29: entries built with another model are not loaded, are deleted and logged; "
          + "the reconcile adds them")
  void model_change_after_a_restart_drops_and_re_adds() {
    PostToIndex a = aPost().id("sounie-wp:1").words(40).build();
    PostToIndex b = aPost().id("elegant:2").words(40).build();
    index.save(INDEXER_R1.index(a));
    index.save(INDEXER_R1.index(b));
    PostIndexer newModel = SearchContext.indexerWith(new FakeEmbedder("model-r2", DIMENSION));

    FileVectorIndex restarted =
        FileVectorIndex.open(dataDirectory, "model-r2", newModel.recipe(), errors);

    assertThat(restarted.ids()).isEmpty();
    assertThat(fileOf(a.id())).doesNotExist();
    assertThat(fileOf(a.id()).resolveSibling(fileOf(a.id()).getFileName() + ".corrupt"))
        .doesNotExist();
    assertThat(errorLines()).hasSize(2);

    ReconcileReport report = reconcile(restarted, newModel, a, b);

    assertThat(report.outcomes())
        .isEqualTo(Map.of(a.id(), IndexOutcome.ADDED, b.id(), IndexOutcome.ADDED));
    assertThat(storedField(fileOf(a.id()), "modelId")).isEqualTo("model-r2");
  }

  @Test
  @DisplayName("AC-APP-47, AC-SRCH-50: a restart with the same profile key reuses its own entries")
  void profile_keyed_index_reuses_entries_after_restart() {
    IndexedPost entry = variedEntries().getFirst();
    FileVectorIndex onnx = profileIndex(PROFILE_ONNX);
    onnx.save(entry);

    FileVectorIndex restarted = profileIndex(PROFILE_ONNX);

    assertThat(fileOf(PROFILE_ONNX, entry.id())).isRegularFile();
    assertSameEntry(restarted.find(entry.id()), entry);
    assertThat(restarted.ids()).containsExactly(entry.id());
  }

  @Test
  @DisplayName("Review #4: an older binary on the same data directory leaves profile entries alone")
  void an_older_binary_does_not_touch_profile_entries() {
    IndexedPost entry = variedEntries().getFirst();
    profileIndex(PROFILE_ONNX).save(entry);
    byte[] stored = StoredFiles.read(fileOf(PROFILE_ONNX, entry.id()));

    // An older binary opens the legacy layout with its own model.
    FileVectorIndex.open(dataDirectory, "older-model", INDEXER_R1.recipe(), errors);

    assertThat(StoredFiles.read(fileOf(PROFILE_ONNX, entry.id()))).containsExactly(stored);
    assertSameEntry(profileIndex(PROFILE_ONNX).find(entry.id()), entry);
  }

  @Test
  @DisplayName("AC-APP-47: the first ONNX open atomically adopts legacy index files")
  void onnx_profile_adopts_legacy_entries_once() {
    IndexedPost entry = variedEntries().getFirst();
    FileVectorIndex legacy =
        FileVectorIndex.open(dataDirectory, MODEL, INDEXER_R1.recipe(), errors);
    legacy.save(entry);

    FileVectorIndex adopted = profileIndex(PROFILE_ONNX);

    assertThat(legacyFileOf(entry.id())).doesNotExist();
    assertThat(fileOf(PROFILE_ONNX, entry.id())).isRegularFile();
    assertSameEntry(adopted.find(entry.id()), entry);
    assertThat(errorLines()).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-50: Gemma2 does not inspect or move legacy ONNX files")
  void gemma_profile_leaves_legacy_onnx_files_untouched() {
    IndexedPost entry = variedEntries().getFirst();
    FileVectorIndex legacy =
        FileVectorIndex.open(dataDirectory, MODEL, INDEXER_R1.recipe(), errors);
    legacy.save(entry);
    byte[] legacyBytes = StoredFiles.read(legacyFileOf(entry.id()));

    FileVectorIndex gemma = profileIndex(PROFILE_GEMMA);

    assertThat(legacyFileOf(entry.id())).isRegularFile();
    assertThat(StoredFiles.read(legacyFileOf(entry.id()))).containsExactly(legacyBytes);
    assertThat(gemma.find(entry.id())).isEmpty();
    assertThat(errorLines()).isEmpty();
  }

  @Test
  @DisplayName("AC-APP-47: legacy adoption never overwrites an existing ONNX profile entry")
  void onnx_adoption_keeps_an_existing_profile_entry() throws IOException {
    IndexedPost entry = variedEntries().getFirst();
    FileVectorIndex legacy =
        FileVectorIndex.open(dataDirectory, MODEL, INDEXER_R1.recipe(), errors);
    legacy.save(entry);
    Path target = fileOf(PROFILE_ONNX, entry.id());
    Files.createDirectories(target.getParent());
    Files.writeString(target, Files.readString(legacyFileOf(entry.id())) + " \n");
    byte[] targetBytes = Files.readAllBytes(target);

    FileVectorIndex onnx = profileIndex(PROFILE_ONNX);

    assertThat(legacyFileOf(entry.id())).isRegularFile();
    assertThat(Files.readAllBytes(target)).containsExactly(targetBytes);
    assertSameEntry(onnx.find(entry.id()), entry);
    assertThat(errorLines()).singleElement().asString().contains("target already exists");
  }

  @Test
  @DisplayName("AC-APP-25: the 768-dimension provider profile survives a bit-exact restart")
  void profile_with_768_dimensions_survives_a_bit_exact_restart() {
    IndexedPost entry =
        indexed(
            aPost().id("sounie-wp:768").title("768-dimensional provider").build(),
            fingerprint('h'),
            TestEmbeddings.irregular(77, 768));
    FileVectorIndex gemma = profileIndex(PROFILE_GEMMA);
    gemma.save(entry);

    IndexedPost reopened = profileIndex(PROFILE_GEMMA).find(entry.id()).orElseThrow();

    assertThat(reopened).usingRecursiveComparison().isEqualTo(entry);
    assertThat(TestEmbeddings.rawBits(reopened.chunks().getFirst().embedding()))
        .containsExactly(TestEmbeddings.rawBits(entry.chunks().getFirst().embedding()));
  }

  @Test
  @DisplayName(
      "AC-SRCH-45, AC-APP-47, AC-SRCH-50: switching profiles neither loads nor mutates the other subtree")
  void switching_profile_keys_isolates_loads_saves_and_deletes() throws IOException {
    IndexedPost onnxEntry = variedEntries().getFirst();
    FileVectorIndex onnx = profileIndex(PROFILE_ONNX);
    onnx.save(onnxEntry);
    byte[] onnxBytes = Files.readAllBytes(fileOf(PROFILE_ONNX, onnxEntry.id()));

    FileVectorIndex gemma = profileIndex(PROFILE_GEMMA);

    assertThat(gemma.ids()).isEmpty();
    assertThat(gemma.find(onnxEntry.id())).isEmpty();
    IndexedPost gemmaEntry =
        indexed(
            aPost().id(onnxEntry.id().external()).title("Gemma profile").build(),
            fingerprint('g'),
            TestEmbeddings.irregular(9));
    gemma.save(gemmaEntry);
    gemma.remove(gemmaEntry.id());

    assertThat(fileOf(PROFILE_ONNX, onnxEntry.id())).isRegularFile();
    assertThat(Files.readAllBytes(fileOf(PROFILE_ONNX, onnxEntry.id()))).containsExactly(onnxBytes);
    assertThat(fileOf(PROFILE_GEMMA, gemmaEntry.id())).doesNotExist();
    assertThat(errorLines()).isEmpty();
  }

  @Test
  @DisplayName(
      "S1, ADR 0007: an index file that cannot be read from disk is quarantined; startup"
          + " continues and the reconcile re-adds the post")
  void index_file_unreadable_from_disk_is_quarantined_and_re_added() throws IOException {
    PostToIndex blocked = aPost().id("sounie-wp:1").title("Blocked").words(50).build();
    PostToIndex intact = aPost().id("sounie-wp:2").title("Intact").words(50).build();
    index.save(INDEXER_R1.index(blocked));
    index.save(INDEXER_R1.index(intact));
    Path file = fileOf(blocked.id());
    Files.setPosixFilePermissions(file, Set.of());
    assumeFalse(Files.isReadable(file), "the file is still readable (running as root?)");

    FileVectorIndex reopened = reopen();

    assertThat(reopened.ids()).containsExactly(intact.id());
    assertThat(file).doesNotExist();
    assertThat(file.resolveSibling(file.getFileName() + ".corrupt")).exists();
    assertThat(errorLines()).singleElement().asString().contains(file.toString());

    ReconcileReport report = reconcile(reopened, INDEXER_R1, blocked, intact);

    assertThat(report.outcomes())
        .isEqualTo(Map.of(blocked.id(), IndexOutcome.ADDED, intact.id(), IndexOutcome.UNCHANGED));
    assertSameEntry(reopen().find(blocked.id()), INDEXER_R1.index(blocked));
  }

  @Test
  @DisplayName("S1: a directory where an index file belongs does not stop startup")
  void directory_in_place_of_an_index_file_does_not_stop_startup() throws IOException {
    IndexedPost kept = variedEntries().get(0);
    index.save(kept);
    Path occupied = fileOf(variedEntries().get(1).id());
    Files.createDirectories(occupied);
    Files.writeString(occupied.resolve("blocker"), "");

    FileVectorIndex reopened = reopen();

    assertThat(reopened.ids()).containsExactly(kept.id());
  }

  @Test
  @DisplayName("S2: an index file without a model ID is quarantined, not deleted")
  void index_file_without_model_id_is_quarantined() throws IOException {
    index.save(first);
    Path file = fileOf(post.id());
    ObjectNode json = (ObjectNode) IndexFileJson.JSON.readTree(Files.readAllBytes(file));
    json.remove("modelId");
    Files.write(file, IndexFileJson.bytes(json));

    FileVectorIndex reopened = reopen();

    assertThat(reopened.ids()).isEmpty();
    assertThat(file).doesNotExist();
    assertThat(file.resolveSibling(file.getFileName() + ".corrupt")).isRegularFile();
    assertThat(errorLines()).singleElement().asString().contains(file.toString());
  }

  @Test
  @DisplayName("S3: an index file found at another post's location is quarantined")
  void index_file_at_another_posts_location_is_quarantined() throws IOException {
    IndexedPost a = variedEntries().get(0);
    IndexedPost b = variedEntries().get(1);
    index.save(b);
    Path pathOfA = fileOf(a.id());
    Files.move(fileOf(b.id()), pathOfA);

    FileVectorIndex reopened = reopen();

    assertThat(reopened.ids()).isEmpty();
    assertThat(pathOfA).doesNotExist();
    assertThat(pathOfA.resolveSibling(pathOfA.getFileName() + ".corrupt")).isRegularFile();
    assertThat(errorLines()).singleElement().asString().contains(pathOfA.toString());
  }

  @Test
  @DisplayName("S3: a removed entry never comes back from a file at another post's location")
  void removed_entry_is_not_resurrected_by_a_misplaced_file() throws IOException {
    IndexedPost a = variedEntries().get(0);
    IndexedPost b = variedEntries().get(1);
    index.save(b);
    Files.move(fileOf(b.id()), fileOf(a.id()));
    FileVectorIndex opened = reopen();

    opened.remove(b.id());

    assertThat(reopen().ids()).isEmpty();
  }

  private static ReconcileReport reconcile(
      VectorIndex index, PostIndexer indexer, PostToIndex... catalogPosts) {
    FakePostCatalog catalog = new FakePostCatalog().holding(catalogPosts);
    return new ReconcileIndex(catalog, index, indexer, new IndexWriteLock()).run();
  }

  private static String storedField(Path file, String field) {
    return IndexFileJson.JSON.readTree(file).get(field).stringValue();
  }

  private static List<String> filesIn(Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files.map(p -> p.getFileName().toString()).toList();
    }
  }
}
