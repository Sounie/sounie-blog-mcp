package nz.sounie.blogmcp.search.application;

import static nz.sounie.blogmcp.search.domain.PostToIndexBuilder.aPost;
import static nz.sounie.blogmcp.search.domain.SearchQueryBuilder.aQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Stream;
import nz.sounie.blogmcp.search.domain.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.IndexChange;
import nz.sounie.blogmcp.search.domain.IndexOutcome;
import nz.sounie.blogmcp.search.domain.IndexedPost;
import nz.sounie.blogmcp.search.domain.Passage;
import nz.sounie.blogmcp.search.domain.PostId;
import nz.sounie.blogmcp.search.domain.PostMatch;
import nz.sounie.blogmcp.search.domain.PostToIndex;
import nz.sounie.blogmcp.search.domain.PostToIndexBuilder;
import nz.sounie.blogmcp.search.domain.PublishedDateRange;
import nz.sounie.blogmcp.search.domain.SearchQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IndexPostTest {

  private static final Instant PUBLISHED = Instant.parse("2024-04-10T00:00:00Z");

  private final SearchContext search = new SearchContext();

  private static PostToIndexBuilder post1() {
    return aPost()
        .id("sounie-wp:1")
        .title("Records in Java 25")
        .words(700)
        .tags("Java")
        .url("https://blog2.sounie.nz/records/")
        .publishedAt(PUBLISHED);
  }

  private IndexOutcome upsert(PostToIndex post) {
    return search.indexPost.apply(new IndexChange.Upsert(post));
  }

  private IndexOutcome withdraw(PostId id) {
    return search.indexPost.apply(new IndexChange.Remove(id));
  }

  private Stream<PostId> found(SearchQuery query) {
    return search.searchPosts.search(query).matches().stream().map(PostMatch::postId);
  }

  @Test
  @DisplayName("AC-SRCH-11: publishing indexes a post")
  void publishing_indexes_a_post() {
    PostToIndex post = post1().build();

    assertThat(upsert(post)).isEqualTo(IndexOutcome.ADDED);

    IndexedPost indexed = search.index.find(post.id()).orElseThrow();
    assertThat(indexed.chunks()).hasSize(3);
    assertThat(indexed.metadata()).isEqualTo(post.metadata());
    assertThat(indexed.fingerprint()).isEqualTo(search.indexer.fingerprintOf(post));
    assertThat(found(aQuery("records java").build())).containsExactly(post.id());
  }

  @Test
  @DisplayName("AC-SRCH-12: delivering the same content again is UNCHANGED, without embedding")
  void indexing_is_idempotent() {
    PostToIndex post = post1().build();
    upsert(post);
    IndexedPost before = search.index.find(post.id()).orElseThrow();
    search.embedder.clearCalls();

    assertThat(upsert(post)).isEqualTo(IndexOutcome.UNCHANGED);
    assertThat(search.embedder.passageCallCount()).isZero();
    assertThat(search.index.find(post.id())).containsSame(before);
  }

  @Test
  @DisplayName("AC-SRCH-13: a body revision replaces the chunks; other posts are untouched")
  void body_revision_replaces_chunks() {
    upsert(post1().build());
    upsert(aPost().id("elegant:7").words(50).build());
    IndexedPost other = search.index.find(PostId.parse("elegant:7")).orElseThrow();

    assertThat(upsert(post1().words(120).build())).isEqualTo(IndexOutcome.RE_EMBEDDED);

    assertThat(search.index.find(PostId.parse("sounie-wp:1")).orElseThrow().chunks()).hasSize(1);
    assertThat(search.index.find(PostId.parse("elegant:7"))).containsSame(other);
  }

  @Test
  @DisplayName("AC-SRCH-13: a title revision re-embeds every passage with the new title")
  void title_revision_re_embeds_with_the_new_title() {
    upsert(post1().build());
    search.embedder.clearCalls();

    assertThat(upsert(post1().title("Records in Java 26").build()))
        .isEqualTo(IndexOutcome.RE_EMBEDDED);

    assertThat(search.embedder.allPassages())
        .hasSize(3)
        .extracting(Passage::text)
        .allSatisfy(text -> assertThat(text).startsWith("Records in Java 26\n"));
    assertThat(search.index.find(PostId.parse("sounie-wp:1")).orElseThrow().chunks()).hasSize(3);
  }

  static Stream<Arguments> metadataOnlyRevisions() {
    return Stream.of(
        Arguments.of("tags", post1().tags("Java", "Records")),
        Arguments.of("url", post1().url("https://blog2.sounie.nz/records-moved/")),
        Arguments.of("published at", post1().publishedAt(Instant.parse("2024-05-20T00:00:00Z"))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("metadataOnlyRevisions")
  @DisplayName("AC-SRCH-14: a metadata-only revision refreshes without embedding")
  void metadata_only_revision_does_not_re_embed(String aspect, PostToIndexBuilder revised) {
    upsert(post1().build());
    IndexedPost before = search.index.find(PostId.parse("sounie-wp:1")).orElseThrow();
    search.embedder.clearCalls();

    assertThat(upsert(revised.build())).isEqualTo(IndexOutcome.METADATA_REFRESHED);

    IndexedPost after = search.index.find(PostId.parse("sounie-wp:1")).orElseThrow();
    assertThat(search.embedder.passageCallCount()).isZero();
    assertThat(after.chunks()).isEqualTo(before.chunks());
    assertThat(after.fingerprint()).isEqualTo(before.fingerprint());
    assertThat(after.metadata()).isEqualTo(revised.metadata());
  }

  @Test
  @DisplayName("AC-SRCH-14: filters use the refreshed published-at")
  void date_filter_uses_the_refreshed_published_at() {
    upsert(post1().build());
    upsert(post1().publishedAt(Instant.parse("2024-05-20T00:00:00Z")).build());

    assertThat(
            found(
                aQuery("records").dates(PublishedDateRange.from(LocalDate.of(2024, 5, 1))).build()))
        .containsExactly(PostId.parse("sounie-wp:1"));
    assertThat(
            found(
                aQuery("records").dates(PublishedDateRange.to(LocalDate.of(2024, 4, 30))).build()))
        .isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-15: a revision for a post that is not indexed adds it")
  void revision_for_unindexed_post_adds_it() {
    assertThat(upsert(aPost().id("elegant:42").words(80).build())).isEqualTo(IndexOutcome.ADDED);
    assertThat(search.index.ids()).containsExactly(PostId.parse("elegant:42"));
  }

  @Test
  @DisplayName("AC-SRCH-16: withdrawal removes the post; again is ALREADY_ABSENT")
  void withdrawal_removes_the_post() {
    PostToIndex post = post1().build();
    upsert(post);

    assertThat(withdraw(post.id())).isEqualTo(IndexOutcome.REMOVED);
    assertThat(search.index.find(post.id())).isEmpty();
    assertThat(found(aQuery("records").build())).isEmpty();
    assertThat(withdraw(post.id())).isEqualTo(IndexOutcome.ALREADY_ABSENT);
    assertThat(withdraw(PostId.parse("elegant:404"))).isEqualTo(IndexOutcome.ALREADY_ABSENT);
  }

  @Test
  @DisplayName("AC-SRCH-17: an embedder failure keeps the old entry, still searchable")
  void embedder_failure_keeps_the_old_entry() {
    PostToIndex post = post1().build();
    upsert(post);
    IndexedPost before = search.index.find(post.id()).orElseThrow();
    search.embedder.failing();

    assertThatThrownBy(() -> upsert(post1().words(120).build()))
        .isInstanceOf(EmbedderUnavailable.class);

    assertThat(search.index.find(post.id())).containsSame(before);
    assertThat(found(aQuery("records").build())).containsExactly(post.id());
  }

  @Test
  @DisplayName("AC-SRCH-33: publishing a summary-only post indexes nothing")
  void summary_publish_indexes_nothing() {
    PostToIndex summary = aPost().id("elegant:5").title("Summary only").summary().build();

    assertThat(upsert(summary)).isEqualTo(IndexOutcome.EXCLUDED);
    assertThat(search.embedder.passageCallCount()).isZero();
    assertThat(search.index.find(summary.id())).isEmpty();
    assertThat(found(aQuery("summary only").build())).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-34: a revision from summary to full adds the post")
  void summary_to_full_adds_the_post() {
    upsert(aPost().id("elegant:5").summary().body("A teaser").build());

    PostToIndex full = aPost().id("elegant:5").words(700).build();

    assertThat(upsert(full)).isEqualTo(IndexOutcome.ADDED);
    assertThat(search.index.find(full.id()))
        .hasValueSatisfying(p -> assertThat(p.chunks()).hasSize(3));
    assertThat(found(aQuery("w1").build())).containsExactly(full.id());
  }

  @Test
  @DisplayName("AC-SRCH-35: a revision from full to summary removes the post; again is EXCLUDED")
  void full_to_summary_removes_the_post() {
    upsert(aPost().id("elegant:5").words(700).build());
    search.embedder.clearCalls();
    PostToIndex summary = aPost().id("elegant:5").summary().body("A teaser").build();

    assertThat(upsert(summary)).isEqualTo(IndexOutcome.REMOVED);
    assertThat(search.index.find(summary.id())).isEmpty();
    assertThat(search.embedder.passageCallCount()).isZero();
    assertThat(found(aQuery("w1").build())).isEmpty();
    assertThat(upsert(summary)).isEqualTo(IndexOutcome.EXCLUDED);
  }
}
