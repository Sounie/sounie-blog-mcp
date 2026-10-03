package nz.sounie.blogmcp.search.adapter.out;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.fingerprint;
import static nz.sounie.blogmcp.search.domain.index.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.index.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.application.IndexWriteLock;
import nz.sounie.blogmcp.search.application.ReconcileIndex;
import nz.sounie.blogmcp.search.application.ReconcileReport;
import nz.sounie.blogmcp.search.application.SearchContext;
import nz.sounie.blogmcp.search.domain.index.IndexOutcome;
import nz.sounie.blogmcp.search.domain.index.IndexedPost;
import nz.sounie.blogmcp.search.domain.index.PostIndexer;
import nz.sounie.blogmcp.search.domain.index.PostToIndex;
import nz.sounie.blogmcp.search.domain.index.VectorIndex;
import nz.sounie.blogmcp.search.domain.post.PostId;
import nz.sounie.blogmcp.search.domain.text.ChunkingPolicy;
import nz.sounie.blogmcp.search.domain.text.PassageComposition;
import nz.sounie.blogmcp.shared.storage.FileKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.node.ObjectNode;

/** Real files in a temporary directory, real Jackson 3, real file system. */
class FileVectorIndexTest extends VectorIndexContract {

  private static final String MODEL = "model-r1";

  /** Recipe R1: the standard chunking, under model-r1. */
  private static final PostIndexer INDEXER_R1 = SearchContext.indexerWith(new FakeEmbedder(MODEL));

  /** Recipe R2: the same model, with different chunking. */
  private static final PostIndexer INDEXER_R2 =
      new PostIndexer(
          new ChunkingPolicy(200, ChunkingPolicy.BODY_TOKEN_BUDGET, 40, 80),
          PassageComposition.standard(),
          FakeTokenCounter.perWord(1),
          new FakeEmbedder(MODEL));

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
    PostIndexer newModel = SearchContext.indexerWith(new FakeEmbedder("model-r2"));

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
