package nz.sounie.blogmcp.search.domain;

import static nz.sounie.blogmcp.search.domain.IndexedPosts.indexed;
import static nz.sounie.blogmcp.search.domain.PostToIndexBuilder.aPost;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import nz.sounie.blogmcp.search.adapter.out.InMemoryVectorIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconcilePlanTest {

  private static final IndexRecipe R1 = new IndexRecipe("r1");
  private static final IndexRecipe R2 = new IndexRecipe("r2");

  private final InMemoryVectorIndex snapshot = new InMemoryVectorIndex();

  private void store(PostToIndex post, IndexRecipe recipe) {
    snapshot.save(
        indexed(
            post,
            ContentFingerprint.of(recipe, post.title(), WordSequence.of(post.body())),
            Vectors.axis(1)));
  }

  private static List<CatalogEntry> readable(PostToIndex... posts) {
    return java.util.Arrays.stream(posts).<CatalogEntry>map(CatalogEntry.Readable::new).toList();
  }

  private static Map<PostId, IndexDecision> byPost(ReconcilePlan plan) {
    return plan.decisions().stream()
        .collect(Collectors.toMap(IndexDecision::postId, Function.identity()));
  }

  @Test
  @DisplayName("AC-SRCH-28: missing, stale, metadata-only, identical and orphan posts")
  void plans_one_decision_per_post_and_orphan() {
    PostToIndex p1 = aPost().id("sounie-wp:1").body("new post").build();
    PostToIndex p2 = aPost().id("sounie-wp:2").body("newer body").build();
    PostToIndex p3 = aPost().id("sounie-wp:3").tags("New").build();
    PostToIndex p4 = aPost().id("sounie-wp:4").build();
    PostToIndex orphan = aPost().id("elegant:9").build();
    store(aPost().id("sounie-wp:2").body("older body").build(), R1);
    store(aPost().id("sounie-wp:3").tags("Old").build(), R1);
    store(p4, R1);
    store(orphan, R1);

    ReconcilePlan plan = ReconcilePlan.between(readable(p1, p2, p3, p4), snapshot, R1);

    Map<PostId, IndexDecision> decisions = byPost(plan);
    assertThat(plan.decisions()).hasSize(5);
    assertThat(decisions.get(p1.id())).isEqualTo(new IndexDecision.Add(p1));
    assertThat(decisions.get(p2.id())).isEqualTo(new IndexDecision.ReEmbed(p2));
    assertThat(decisions.get(p3.id())).isInstanceOf(IndexDecision.RefreshMetadata.class);
    assertThat(decisions.get(p4.id())).isEqualTo(new IndexDecision.Keep(p4.id()));
    assertThat(decisions.get(orphan.id())).isEqualTo(new IndexDecision.Remove(orphan.id()));
  }

  @Test
  @DisplayName("AC-SRCH-29: under a new recipe every indexed catalog post is stale")
  void a_recipe_change_re_embeds_everything() {
    PostToIndex a = aPost().id("sounie-wp:1").build();
    PostToIndex b = aPost().id("elegant:2").build();
    store(a, R1);
    store(b, R1);

    ReconcilePlan plan = ReconcilePlan.between(readable(a, b), snapshot, R2);

    assertThat(plan.decisions())
        .containsExactlyInAnyOrder(new IndexDecision.ReEmbed(a), new IndexDecision.ReEmbed(b));
  }

  @Test
  @DisplayName("AC-SRCH-29: against an empty index every catalog post is added")
  void a_rebuild_adds_everything() {
    PostToIndex a = aPost().id("sounie-wp:1").build();
    PostToIndex b = aPost().id("elegant:2").build();

    assertThat(ReconcilePlan.between(readable(a, b), snapshot, R1).decisions())
        .containsExactlyInAnyOrder(new IndexDecision.Add(a), new IndexDecision.Add(b));
  }

  @Test
  @DisplayName("AC-SRCH-30: an empty catalog removes every indexed post")
  void empty_catalog_removes_everything() {
    PostToIndex a = aPost().id("sounie-wp:1").build();
    PostToIndex b = aPost().id("elegant:2").build();
    store(a, R1);
    store(b, R1);

    assertThat(ReconcilePlan.between(List.of(), snapshot, R1).decisions())
        .containsExactlyInAnyOrder(
            new IndexDecision.Remove(a.id()), new IndexDecision.Remove(b.id()));
  }

  @Test
  @DisplayName("AC-SRCH-36: summary-only catalog posts are excluded, not orphans")
  void summary_posts_are_excluded_not_orphaned() {
    PostToIndex s1 = aPost().id("elegant:1").summary().build();
    PostToIndex s2 = aPost().id("elegant:2").summary().build();
    PostToIndex f1 = aPost().id("sounie-wp:1").build();
    store(aPost().id("elegant:2").build(), R1);

    ReconcilePlan plan = ReconcilePlan.between(readable(s1, s2, f1), snapshot, R1);

    assertThat(plan.decisions())
        .containsExactlyInAnyOrder(
            new IndexDecision.Exclude(s1.id()),
            new IndexDecision.Exclude(s2.id()),
            new IndexDecision.Add(f1));
  }

  @Test
  @DisplayName(
      "AC-SRCH-30: without unidentified entries, orphans are removed and nothing is suppressed")
  void orphans_are_removed_when_every_entry_is_identified() {
    PostToIndex kept = aPost().id("sounie-wp:1").build();
    PostToIndex orphan = aPost().id("elegant:9").build();
    store(kept, R1);
    store(orphan, R1);

    ReconcilePlan plan = ReconcilePlan.between(readable(kept), snapshot, R1);

    assertThat(plan.decisions()).contains(new IndexDecision.Remove(orphan.id()));
    assertThat(plan.orphanRemovalSuppressed()).isFalse();
  }

  @Test
  @DisplayName("AC-SRCH-38: an unreadable indexed post plans Unreadable, never Remove")
  void unreadable_indexed_post_is_not_removed() {
    PostToIndex indexedPost = aPost().id("sounie-wp:1").build();
    store(indexedPost, R1);

    ReconcilePlan plan =
        ReconcilePlan.between(
            List.of(new CatalogEntry.Unreadable(indexedPost.id(), "completeness is PARTIAL")),
            snapshot,
            R1);

    assertThat(plan.decisions())
        .containsExactly(new IndexDecision.Unreadable(indexedPost.id(), "completeness is PARTIAL"));
  }

  @Test
  @DisplayName("AC-SRCH-38: an unreadable post that is not indexed plans Unreadable, not an add")
  void unreadable_unindexed_post_is_planned_as_unreadable() {
    PostId id = PostId.parse("elegant:3");

    ReconcilePlan plan =
        ReconcilePlan.between(
            List.of(new CatalogEntry.Unreadable(id, "title is missing")), snapshot, R1);

    assertThat(plan.decisions())
        .containsExactly(new IndexDecision.Unreadable(id, "title is missing"));
  }

  @Test
  @DisplayName("AC-SRCH-38: an unreadable entry's ID is not an orphan; real orphans still go")
  void unreadable_id_is_not_an_orphan() {
    PostToIndex unreadable = aPost().id("sounie-wp:1").build();
    PostToIndex orphan = aPost().id("elegant:9").build();
    store(unreadable, R1);
    store(orphan, R1);

    ReconcilePlan plan =
        ReconcilePlan.between(
            List.of(new CatalogEntry.Unreadable(unreadable.id(), "bad URL")), snapshot, R1);

    assertThat(plan.decisions())
        .containsExactlyInAnyOrder(
            new IndexDecision.Unreadable(unreadable.id(), "bad URL"),
            new IndexDecision.Remove(orphan.id()));
    assertThat(plan.orphanRemovalSuppressed()).isFalse();
  }

  @Test
  @DisplayName("AC-SRCH-38: any unidentified entry suppresses every orphan removal")
  void unidentified_entry_suppresses_orphan_removal_with_its_reason() {
    PostToIndex readable = aPost().id("sounie-wp:1").build();
    PostToIndex maybeOrphan = aPost().id("elegant:9").build();
    store(readable, R1);
    store(maybeOrphan, R1);

    ReconcilePlan plan =
        ReconcilePlan.between(
            List.of(
                new CatalogEntry.Readable(readable),
                new CatalogEntry.Unidentified("post ID 'elegant-9' is not <siteId>:<id>")),
            snapshot,
            R1);

    assertThat(plan.decisions()).containsExactly(new IndexDecision.Keep(readable.id()));
    assertThat(plan.orphanRemovalSuppressed()).isTrue();
    assertThat(plan.orphanRemovalSuppressedBy())
        .containsExactly("post ID 'elegant-9' is not <siteId>:<id>");
  }

  @Test
  @DisplayName("AC-SRCH-38: a catalog of only unidentified entries removes nothing")
  void only_unidentified_entries_remove_nothing() {
    store(aPost().id("sounie-wp:1").build(), R1);

    ReconcilePlan plan =
        ReconcilePlan.between(List.of(new CatalogEntry.Unidentified("no ID")), snapshot, R1);

    assertThat(plan.decisions()).isEmpty();
    assertThat(plan.orphanRemovalSuppressed()).isTrue();
    assertThat(plan.orphanRemovalSuppressedBy()).containsExactly("no ID");
  }

  @Test
  @DisplayName("S2: a post ID listed twice is rejected as unreadable, not merged")
  void duplicate_post_id_is_rejected() {
    PostToIndex first = aPost().id("sounie-wp:1").title("First").build();
    PostToIndex second = aPost().id("sounie-wp:1").title("Second").build();
    store(first, R1);

    ReconcilePlan plan = ReconcilePlan.between(readable(first, second), snapshot, R1);

    assertThat(plan.decisions())
        .singleElement()
        .isInstanceOfSatisfying(
            IndexDecision.Unreadable.class,
            decision -> {
              assertThat(decision.postId()).isEqualTo(first.id());
              assertThat(decision.reason()).containsIgnoringCase("duplicate");
            });
  }

  @Test
  @DisplayName("S2: a duplicate ID does not stop the other posts being planned")
  void duplicate_post_id_leaves_other_posts_planned() {
    PostToIndex dup = aPost().id("sounie-wp:1").build();
    PostToIndex other = aPost().id("elegant:2").build();

    ReconcilePlan plan = ReconcilePlan.between(readable(dup, other, dup), snapshot, R1);

    assertThat(plan.decisions()).hasSize(2);
    assertThat(byPost(plan))
        .hasSize(2)
        .containsEntry(other.id(), new IndexDecision.Add(other))
        .hasEntrySatisfying(
            dup.id(),
            decision -> assertThat(decision).isInstanceOf(IndexDecision.Unreadable.class));
  }

  @Test
  @DisplayName("AC-SRCH-38: two unidentified entries suppress orphan removal with both reasons")
  void unidentified_entries_suppress_orphan_removal_with_every_reason() {
    store(aPost().id("elegant:9").build(), R1);

    ReconcilePlan plan =
        ReconcilePlan.between(
            List.of(
                new CatalogEntry.Unidentified("post ID 'not-an-id' is not <siteId>:<id>"),
                new CatalogEntry.Readable(aPost().id("sounie-wp:1").build()),
                new CatalogEntry.Unidentified("post ID is missing")),
            snapshot,
            R1);

    assertThat(plan.orphanRemovalSuppressedBy())
        .containsExactly("post ID 'not-an-id' is not <siteId>:<id>", "post ID is missing");
    assertThat(plan.decisions()).noneMatch(IndexDecision.Remove.class::isInstance);
  }

  @Test
  @DisplayName("AC-SRCH-38: with no unidentified entry there is no suppression reason")
  void identified_catalog_gives_no_suppression_reasons() {
    ReconcilePlan plan =
        ReconcilePlan.between(
            List.of(
                new CatalogEntry.Readable(aPost().id("sounie-wp:1").build()),
                new CatalogEntry.Unreadable(PostId.parse("sounie-wp:2"), "bad URL")),
            snapshot,
            R1);

    assertThat(plan.orphanRemovalSuppressedBy()).isEmpty();
  }
}
