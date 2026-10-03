package nz.sounie.blogmcp.catalog.adapter.out;

import static java.nio.charset.StandardCharsets.UTF_8;
import static nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder.aSnapshot;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import nz.sounie.blogmcp.catalog.domain.post.BodyCompleteness;
import nz.sounie.blogmcp.catalog.domain.post.Post;
import nz.sounie.blogmcp.catalog.domain.post.PostId;
import nz.sounie.blogmcp.catalog.domain.post.PostRepository;
import nz.sounie.blogmcp.catalog.domain.post.PostSnapshotBuilder;
import nz.sounie.blogmcp.catalog.domain.post.Tag;
import nz.sounie.blogmcp.catalog.domain.site.TestSites;
import nz.sounie.blogmcp.shared.storage.FileKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Real files in a temporary directory, real Jackson 3, real file system. */
class FilePostRepositoryTest extends PostRepositoryContract {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Instant BASE = PostSnapshotBuilder.DEFAULT_PUBLISHED_AT;
  private static final String ODD_SOURCE_ID = "tag:blogger.com,1999:blog-1.post-9/ü";

  @TempDir Path dataDirectory;

  private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
  private final PrintStream errors = new PrintStream(errorBytes, true, UTF_8);

  @Override
  protected PostRepository newRepository() {
    return FilePostRepository.open(dataDirectory, errors);
  }

  private FilePostRepository reopen() {
    return FilePostRepository.open(dataDirectory, errors);
  }

  private Path postsOf(String siteId) {
    return dataDirectory.resolve("catalog").resolve("posts").resolve(siteId);
  }

  private Path fileOf(PostId id) {
    return postsOf(id.siteId().value())
        .resolve(FileKey.of(id.sourcePostId().value()).value() + ".json");
  }

  private List<String> errorLines() {
    return errorBytes.toString(UTF_8).lines().toList();
  }

  private static List<Post> variedPosts() {
    return List.of(
        aSnapshot().sourcePostId("1").tags("Java", "gRPC", "kotlin").buildStoredPost(),
        aSnapshot().sourcePostId("2").body("").buildStoredPost(),
        aSnapshot()
            .on(TestSites.ELEGANT)
            .sourcePostId("3")
            .url("https://blog.elegant-solutions.london/2020/01/summary.html")
            .completeness(BodyCompleteness.SUMMARY)
            .body("Only the summary.")
            .buildStoredPost(),
        aSnapshot()
            .sourcePostId("4")
            .title("Kia ora — Māori, 日本語 and 🌏")
            .publishedAt(Instant.parse("2024-03-31T11:30:00.123Z"))
            .updatedAt(Instant.parse("2024-04-02T08:00:00.000000001Z"))
            .buildStoredPost(),
        aSnapshot()
            .sourcePostId(ODD_SOURCE_ID)
            .url("https://blog2.sounie.nz/odd/")
            .buildStoredPost());
  }

  @Test
  @DisplayName("AC-APP-24: posts survive a restart, and every lookup returns equal values")
  void posts_survive_a_restart() {
    List<Post> posts = variedPosts();
    posts.forEach(repository::save);

    FilePostRepository reopened = reopen();

    posts.forEach(
        post -> {
          assertSamePost(reopened.findById(post.id()), post);
          assertSamePost(reopened.findByCanonicalUrl(post.url()), post);
        });
    assertThat(reopened.findById(posts.get(0).id()).orElseThrow().tags())
        .extracting(Tag::value)
        .containsExactly("Java", "gRPC", "kotlin");
    assertThat(reopened.findIdsBySite(TestSites.SOUNIE_WP_ID))
        .containsExactlyInAnyOrder(
            posts.get(0).id(), posts.get(1).id(), posts.get(3).id(), posts.get(4).id());
    assertThat(reopened.findIdsBySite(TestSites.ELEGANT_ID)).containsExactly(posts.get(2).id());
    assertThat(reopened.findSiteIds())
        .containsExactlyInAnyOrder(TestSites.SOUNIE_WP_ID, TestSites.ELEGANT_ID);
    assertThat(reopened.health()).isEqualTo(StorageHealth.HEALTHY);
    assertThat(errorLines()).isEmpty();
  }

  @Test
  @DisplayName("AC-APP-24: one file per post at catalog/posts/<site>/<file key>.json")
  void stores_one_file_per_post_named_by_its_file_key() {
    variedPosts().forEach(repository::save);

    assertThat(postsOf("sounie-wp").resolve("1.json")).isRegularFile();
    assertThat(postsOf("elegant").resolve("3.json")).isRegularFile();
    assertThat(postsOf("sounie-wp").resolve(FileKey.of(ODD_SOURCE_ID).value() + ".json"))
        .isRegularFile();
  }

  @Test
  @DisplayName("AC-APP-24: delete removes the file, and the deletion survives a restart")
  void deletion_survives_a_restart() {
    Post kept = aSnapshot().sourcePostId("1").buildStoredPost();
    Post deleted = aSnapshot().sourcePostId("2").buildStoredPost();
    repository.save(kept);
    repository.save(deleted);

    repository.delete(deleted.id());

    assertThat(fileOf(deleted.id())).doesNotExist();
    FilePostRepository reopened = reopen();
    assertThat(reopened.findById(deleted.id())).isEmpty();
    assertThat(reopened.findIdsBySite(TestSites.SOUNIE_WP_ID)).containsExactly(kept.id());
  }

  @Test
  void opens_a_data_directory_that_does_not_exist_yet_as_empty_and_healthy() {
    FilePostRepository opened =
        FilePostRepository.open(dataDirectory.resolve("not-created-yet"), errors);

    assertThat(opened.findSiteIds()).isEmpty();
    assertThat(opened.health()).isEqualTo(StorageHealth.HEALTHY);
  }

  @Test
  @DisplayName("AC-APP-26: every save moves its temporary file into place, leaving none behind")
  void saves_leave_no_temporary_files() throws IOException {
    Post post = POST_123.buildStoredPost();
    repository.save(post);
    repository.save(revisedSnapshot().buildStoredPost());

    assertThat(filesIn(postsOf("sounie-wp"))).containsExactly("123.json");
  }

  @Test
  @DisplayName("AC-APP-26: a leftover partial temporary file is deleted and never read")
  void leftover_temporary_file_is_deleted_and_ignored() throws IOException {
    Post post = POST_123.buildStoredPost();
    repository.save(post);
    Path leftover = postsOf("sounie-wp").resolve("123.json.tmp");
    Files.writeString(leftover, "{\"format\":1,\"postId\":\"sounie-wp:123\",\"tit");

    FilePostRepository reopened = reopen();

    assertSamePost(reopened.findById(post.id()), post);
    assertThat(leftover).doesNotExist();
    assertThat(reopened.health()).isEqualTo(StorageHealth.HEALTHY);
    assertThat(errorLines()).isEmpty();
  }

  static Stream<Arguments> corruptions() {
    return Stream.of(
        Arguments.of("truncated", corruptBytes(bytes -> Arrays.copyOf(bytes, bytes.length / 2))),
        Arguments.of("not JSON", corruptBytes(bytes -> "not json at all".getBytes(UTF_8))),
        Arguments.of("unknown format", corruptJson(json -> json.put("format", 99))),
        Arguments.of(
            "http canonical URL",
            corruptJson(json -> json.put("canonicalUrl", "http://blog2.sounie.nz/123/"))),
        Arguments.of(
            "missing title",
            corruptJson(
                json -> {
                  json.remove("title");
                  return json;
                })));
  }

  private static UnaryOperator<byte[]> corruptBytes(UnaryOperator<byte[]> change) {
    return change;
  }

  private static UnaryOperator<byte[]> corruptJson(UnaryOperator<ObjectNode> change) {
    return bytes -> JSON.writeValueAsBytes(change.apply((ObjectNode) JSON.readTree(bytes)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("corruptions")
  @DisplayName("AC-APP-27: a corrupt post file is quarantined and logged; the others load")
  void corrupt_post_file_is_quarantined(String kind, UnaryOperator<byte[]> corrupt)
      throws IOException {
    Post corrupted = POST_123.buildStoredPost();
    Post healthy = aSnapshot().sourcePostId("7").buildStoredPost();
    repository.save(corrupted);
    repository.save(healthy);
    Path file = fileOf(corrupted.id());
    Files.write(file, corrupt.apply(Files.readAllBytes(file)));

    FilePostRepository reopened = reopen();

    assertThat(reopened.findById(corrupted.id())).isEmpty();
    assertThat(reopened.findByCanonicalUrl(corrupted.url())).isEmpty();
    assertSamePost(reopened.findById(healthy.id()), healthy);
    assertThat(file).doesNotExist();
    assertThat(file.resolveSibling("123.json.corrupt")).isRegularFile();
    assertThat(errorLines())
        .singleElement()
        .asString()
        .contains(file.toString(), "will be fetched again by a reconcile");
    assertThat(reopened.health()).isEqualTo(StorageHealth.DAMAGED);
  }

  @Test
  @DisplayName("AC-APP-40: an unsaved revision is not visible after a restart; a saved one is")
  void only_saved_revisions_survive_a_restart() {
    Post stored = POST_123.buildStoredPost();
    repository.save(stored);
    Post found = repository.findById(stored.id()).orElseThrow();
    found.revise(TestSites.SOUNIE_WP, revisedSnapshot().build());

    assertSamePost(reopen().findById(stored.id()), stored);

    repository.save(found);

    assertSamePost(reopen().findById(stored.id()), revisedSnapshot().buildStoredPost());
  }

  @Test
  @DisplayName("AC-APP-40: while one thread revises and saves, readers only see complete versions")
  void concurrent_readers_never_see_a_half_updated_post() throws Exception {
    repository.save(version(0).buildStoredPost());
    AtomicBoolean writing = new AtomicBoolean(true);
    CountDownLatch start = new CountDownLatch(1);

    try (ExecutorService threads = Executors.newFixedThreadPool(3)) {
      Future<?> writer =
          threads.submit(
              () -> {
                try {
                  start.await();
                  for (int v = 1; v <= 200; v++) {
                    Post post = repository.findById(POST_123.postId()).orElseThrow();
                    post.revise(TestSites.SOUNIE_WP, version(v).build());
                    repository.save(post);
                  }
                } finally {
                  writing.set(false);
                }
                return null;
              });
      List<Future<List<String>>> readers =
          List.of(
              threads.submit(() -> read(start, writing)),
              threads.submit(() -> read(start, writing)));
      start.countDown();

      writer.get(2, TimeUnit.MINUTES);
      for (Future<List<String>> reader : readers) {
        assertThat(reader.get(2, TimeUnit.MINUTES)).isEmpty();
      }
    }
    assertThat(repository.findById(POST_123.postId()).orElseThrow().updatedAt())
        .isEqualTo(BASE.plusSeconds(200));
  }

  /** Version {@code v}: title, body and tag all say {@code v}, updated {@code v} seconds later. */
  private static PostSnapshotBuilder version(int v) {
    return aSnapshot()
        .sourcePostId("123")
        .url("https://blog2.sounie.nz/2026/09/20/hello/")
        .title("v" + v)
        .body("body of v" + v)
        .tags("v" + v)
        .updatedAt(BASE.plusSeconds(v));
  }

  /** Reads until the writer finishes; returns every inconsistent read. */
  private List<String> read(CountDownLatch start, AtomicBoolean writing)
      throws InterruptedException {
    List<String> problems = new ArrayList<>();
    start.await();
    do {
      Post post = repository.findById(POST_123.postId()).orElseThrow();
      long v = post.updatedAt().getEpochSecond() - BASE.getEpochSecond();
      String expected = "v" + v;
      boolean consistent =
          post.title().value().equals(expected)
              && post.body().text().equals("body of " + expected)
              && post.tags().equals(Set.of(new Tag(expected)));
      if (!consistent) {
        problems.add("mixed post at " + post.updatedAt() + ": " + post.title().value());
      }
    } while (writing.get());
    return problems;
  }

  private static List<String> filesIn(Path dir) throws IOException {
    try (Stream<Path> files = Files.list(dir)) {
      return files.map(p -> p.getFileName().toString()).toList();
    }
  }
}
