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

    ReconcilePlan plan = ReconcilePlan.between(List.of(p1, p2, p3, p4), snapshot, R1);

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

    ReconcilePlan plan = ReconcilePlan.between(List.of(a, b), snapshot, R2);

    assertThat(plan.decisions())
        .containsExactlyInAnyOrder(new IndexDecision.ReEmbed(a), new IndexDecision.ReEmbed(b));
  }

  @Test
  @DisplayName("AC-SRCH-29: against an empty index every catalog post is added")
  void a_rebuild_adds_everything() {
    PostToIndex a = aPost().id("sounie-wp:1").build();
    PostToIndex b = aPost().id("elegant:2").build();

    assertThat(ReconcilePlan.between(List.of(a, b), snapshot, R1).decisions())
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

    ReconcilePlan plan = ReconcilePlan.between(List.of(s1, s2, f1), snapshot, R1);

    assertThat(plan.decisions())
        .containsExactlyInAnyOrder(
            new IndexDecision.Exclude(s1.id()),
            new IndexDecision.Exclude(s2.id()),
            new IndexDecision.Add(f1));
  }
}
