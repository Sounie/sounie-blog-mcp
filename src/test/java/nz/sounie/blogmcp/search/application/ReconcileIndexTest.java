package nz.sounie.blogmcp.search.application;

import static nz.sounie.blogmcp.search.domain.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import nz.sounie.blogmcp.search.adapter.out.FakeEmbedder;
import nz.sounie.blogmcp.search.domain.CatalogEntry;
import nz.sounie.blogmcp.search.domain.IndexOutcome;
import nz.sounie.blogmcp.search.domain.Passage;
import nz.sounie.blogmcp.search.domain.PostId;
import nz.sounie.blogmcp.search.domain.PostToIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconcileIndexTest {

  private final SearchContext search = new SearchContext(new FakeEmbedder("model-r1"));

  private void alreadyIndexed(PostToIndex post) {
    search.index.save(search.indexer.index(post));
  }

  private static PostId id(String external) {
    return PostId.parse(external);
  }

  @Test
  @DisplayName("AC-SRCH-28: adds missing, re-embeds stale, refreshes metadata, removes orphans")
  void reconciles_every_kind_of_difference() {
    PostToIndex p1 = aPost().id("sounie-wp:1").title("P one").words(100).build();
    PostToIndex p2 = aPost().id("sounie-wp:2").title("P two").body("the newer body").build();
    PostToIndex p3 = aPost().id("sounie-wp:3").title("P three").tags("New").build();
    PostToIndex p4 = aPost().id("sounie-wp:4").title("P four").build();
    alreadyIndexed(aPost().id("sounie-wp:2").title("P two").body("an older body").build());
    alreadyIndexed(aPost().id("sounie-wp:3").title("P three").tags("Old").build());
    alreadyIndexed(p4);
    alreadyIndexed(aPost().id("elegant:9").title("Orphan").build());
    search.catalog.holding(p1, p2, p3, p4);
    search.embedder.clearCalls();

    ReconcileReport report = search.reconcile.run();

    assertThat(report.outcomes())
        .isEqualTo(
            Map.of(
                p1.id(), IndexOutcome.ADDED,
                p2.id(), IndexOutcome.RE_EMBEDDED,
                p3.id(), IndexOutcome.METADATA_REFRESHED,
                p4.id(), IndexOutcome.UNCHANGED,
                id("elegant:9"), IndexOutcome.REMOVED));
    assertThat(report.count(IndexOutcome.ADDED)).isEqualTo(1);
    assertThat(report.count(IndexOutcome.RE_EMBEDDED)).isEqualTo(1);
    assertThat(report.count(IndexOutcome.METADATA_REFRESHED)).isEqualTo(1);
    assertThat(report.count(IndexOutcome.UNCHANGED)).isEqualTo(1);
    assertThat(report.count(IndexOutcome.REMOVED)).isEqualTo(1);
    assertThat(report.failed()).isEmpty();
    assertThat(search.index.ids()).containsExactlyInAnyOrder(p1.id(), p2.id(), p3.id(), p4.id());
    assertThat(search.embedder.passageCallCount()).isEqualTo(2);
    assertThat(search.embedder.allPassages())
        .extracting(Passage::text)
        .noneMatch(text -> text.startsWith("P three") || text.startsWith("P four"));
  }

  @Test
  @DisplayName("AC-SRCH-28: a second reconcile changes nothing")
  void second_run_is_unchanged() {
    search.catalog.holding(
        aPost().id("sounie-wp:1").words(400).build(), aPost().id("elegant:2").build());
    search.reconcile.run();
    search.embedder.clearCalls();

    ReconcileReport again = search.reconcile.run();

    assertThat(again.outcomes().values()).containsOnly(IndexOutcome.UNCHANGED).hasSize(2);
    assertThat(search.embedder.passageCallCount()).isZero();
  }

  @Test
  @DisplayName("AC-SRCH-29: a recipe change re-embeds every catalog post")
  void recipe_change_re_embeds_everything() {
    PostToIndex a = aPost().id("sounie-wp:1").build();
    PostToIndex b = aPost().id("elegant:2").build();
    alreadyIndexed(a);
    alreadyIndexed(b);
    search.catalog.holding(a, b);

    ReconcileReport report = search.reconcileWith(new FakeEmbedder("model-r2")).run();

    assertThat(report.outcomes())
        .isEqualTo(Map.of(a.id(), IndexOutcome.RE_EMBEDDED, b.id(), IndexOutcome.RE_EMBEDDED));
  }

  @Test
  @DisplayName("AC-SRCH-29: a rebuild is a reconcile against an empty index")
  void rebuild_adds_everything() {
    PostToIndex a = aPost().id("sounie-wp:1").build();
    PostToIndex b = aPost().id("elegant:2").build();
    search.catalog.holding(a, b);

    ReconcileReport report = search.reconcile.run();

    assertThat(report.count(IndexOutcome.ADDED)).isEqualTo(2);
    assertThat(search.index.ids()).containsExactlyInAnyOrder(a.id(), b.id());
  }

  @Test
  @DisplayName("AC-SRCH-30: a failure for one post is FAILED and the others go ahead")
  void carries_on_past_a_failure() {
    PostToIndex first = aPost().id("sounie-wp:1").title("First").build();
    PostToIndex second = aPost().id("sounie-wp:2").title("Second").build();
    PostToIndex third = aPost().id("sounie-wp:3").title("Third").build();
    search.catalog.holding(first, second, third);
    search.embedder.failingForPassagesContaining("Second");

    ReconcileReport report = search.reconcile.run();

    assertThat(report.outcomes())
        .isEqualTo(
            Map.of(
                first.id(), IndexOutcome.ADDED,
                second.id(), IndexOutcome.FAILED,
                third.id(), IndexOutcome.ADDED));
    assertThat(report.failed()).containsExactly(second.id());
    assertThat(report.failureReasons())
        .containsOnlyKeys(second.id())
        .hasEntrySatisfying(
            second.id(), reason -> assertThat(reason).contains("fake embedder failure"));
    assertThat(search.index.ids()).containsExactlyInAnyOrder(first.id(), third.id());
  }

  @Test
  @DisplayName("AC-SRCH-30: an empty catalog empties the index")
  void empty_catalog_empties_the_index() {
    alreadyIndexed(aPost().id("sounie-wp:1").build());
    alreadyIndexed(aPost().id("elegant:2").build());

    ReconcileReport report = search.reconcile.run();

    assertThat(report.count(IndexOutcome.REMOVED)).isEqualTo(2);
    assertThat(search.index.ids()).isEmpty();
  }

  @Test
  @DisplayName("AC-SRCH-36: summary-only posts are excluded; only full posts are embedded")
  void excludes_summary_only_posts() {
    PostToIndex s1 = aPost().id("elegant:1").title("S one").summary().build();
    PostToIndex s2 = aPost().id("elegant:2").title("S two").summary().build();
    PostToIndex f1 = aPost().id("sounie-wp:1").title("F one").build();
    alreadyIndexed(aPost().id("elegant:2").title("S two").build());
    search.catalog.holding(s1, s2, f1);
    search.embedder.clearCalls();

    ReconcileReport report = search.reconcile.run();

    assertThat(report.outcomes())
        .isEqualTo(
            Map.of(
                s1.id(), IndexOutcome.EXCLUDED,
                s2.id(), IndexOutcome.REMOVED,
                f1.id(), IndexOutcome.ADDED));
    assertThat(report.count(IndexOutcome.EXCLUDED)).isEqualTo(1);
    assertThat(report.count(IndexOutcome.REMOVED)).isEqualTo(1);
    assertThat(search.embedder.allPassages())
        .extracting(Passage::text)
        .allSatisfy(text -> assertThat(text).startsWith("F one"));
    assertThat(search.index.ids()).containsExactly(f1.id());

    ReconcileReport again = search.reconcile.run();

    assertThat(again.outcomes())
        .isEqualTo(
            Map.of(
                s1.id(), IndexOutcome.EXCLUDED,
                s2.id(), IndexOutcome.EXCLUDED,
                f1.id(), IndexOutcome.UNCHANGED));
  }

  @Test
  @DisplayName(
      "AC-SRCH-38: an indexed post whose catalog state is malformed stays, reported FAILED")
  void malformed_catalog_state_keeps_the_indexed_post() {
    PostToIndex post = aPost().id("sounie-wp:1").title("Kept").build();
    alreadyIndexed(post);
    var before = search.index.find(post.id()).orElseThrow();
    search.catalog.holdingEntries(
        new CatalogEntry.Unreadable(post.id(), "completeness is PARTIAL"));

    ReconcileReport report = search.reconcile.run();

    assertThat(report.outcomes()).isEqualTo(Map.of(post.id(), IndexOutcome.FAILED));
    assertThat(report.failureReasons()).containsEntry(post.id(), "completeness is PARTIAL");
    assertThat(report.orphanRemovalSuppressed()).isFalse();
    assertThat(search.index.find(post.id())).containsSame(before);
  }

  @Test
  @DisplayName(
      "AC-SRCH-38: an unidentified entry suppresses orphan removal with its reason; others proceed")
  void unidentified_entry_suppresses_orphan_removal_and_others_proceed() {
    PostToIndex listed = aPost().id("sounie-wp:1").build();
    PostToIndex unlisted = aPost().id("elegant:9").build();
    PostToIndex fresh = aPost().id("sounie-wp:5").title("Fresh").build();
    alreadyIndexed(listed);
    alreadyIndexed(unlisted);
    String reason = "post ID 'elegant-9' is not <siteId>:<sourcePostId>";
    search.catalog.holdingEntries(
        new CatalogEntry.Readable(listed),
        new CatalogEntry.Unidentified(reason),
        new CatalogEntry.Readable(fresh));

    ReconcileReport report = search.reconcile.run();

    assertThat(report.outcomes())
        .isEqualTo(Map.of(listed.id(), IndexOutcome.UNCHANGED, fresh.id(), IndexOutcome.ADDED));
    assertThat(report.orphanRemovalSuppressed()).isTrue();
    assertThat(report.orphanRemovalSuppressedReasons()).containsExactly(reason);
    assertThat(report.count(IndexOutcome.REMOVED)).isZero();
    assertThat(search.index.ids())
        .containsExactlyInAnyOrder(listed.id(), unlisted.id(), fresh.id());
  }
}
