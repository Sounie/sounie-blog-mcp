package nz.sounie.blogmcp.catalog.domain;

import static nz.sounie.blogmcp.catalog.domain.PostSnapshotBuilder.aSnapshot;
import static nz.sounie.blogmcp.catalog.domain.TestSites.ELEGANT;
import static nz.sounie.blogmcp.catalog.domain.TestSites.SOUNIE_WP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PostTest {

  private static final Instant T1 = Instant.parse("2026-09-20T01:20:47Z");
  private static final Instant T2 = T1.plusSeconds(3600);

  @Nested
  class Publishing {

    @Test
    void publishing_raises_PostPublished_carrying_the_full_post_state() {
      PostSnapshot snapshot =
          aSnapshot()
              .sourcePostId("123")
              .url("https://blog2.sounie.nz/2026/09/20/hello/")
              .title("Hello")
              .body("Hello, world.")
              .tags("DDD", "Java")
              .publishedAt(T1)
              .updatedAt(T2)
              .build();

      Post.Published published = Post.publish(SOUNIE_WP, snapshot);

      PostPublished event = published.event();
      assertThat(event.postId()).isEqualTo(snapshot.id());
      assertThat(event.siteId()).isEqualTo(SOUNIE_WP.id());
      assertThat(event.url()).isEqualTo(snapshot.url());
      assertThat(event.title()).isEqualTo(new Title("Hello"));
      assertThat(event.body()).isEqualTo(new Body("Hello, world."));
      assertThat(event.completeness()).isEqualTo(BodyCompleteness.FULL);
      assertThat(event.tags()).extracting(Tag::value).containsExactlyInAnyOrder("DDD", "Java");
      assertThat(event.publishedAt()).isEqualTo(T1);
      assertThat(event.updatedAt()).isEqualTo(T2);
    }

    @Test
    void published_post_holds_the_snapshot_state() {
      PostSnapshot snapshot = aSnapshot().title("Hello").publishedAt(T1).updatedAt(T2).build();

      Post post = Post.publish(SOUNIE_WP, snapshot).post();

      assertThat(post.id()).isEqualTo(snapshot.id());
      assertThat(post.url()).isEqualTo(snapshot.url());
      assertThat(post.title()).isEqualTo(new Title("Hello"));
      assertThat(post.body()).isEqualTo(snapshot.body());
      assertThat(post.completeness()).isEqualTo(snapshot.completeness());
      assertThat(post.tags()).isEqualTo(snapshot.tags());
      assertThat(post.publishedAt()).isEqualTo(T1);
      assertThat(post.updatedAt()).isEqualTo(T2);
    }

    @Test
    @DisplayName("AC-CAT-16: a snapshot whose URL is not on the site host is invalid")
    void rejects_a_snapshot_whose_url_is_not_on_the_site_host() {
      PostSnapshot snapshot = aSnapshot().url("https://evil.example/x").build();

      assertThatThrownBy(() -> Post.publish(SOUNIE_WP, snapshot))
          .isInstanceOf(CanonicalUrlNotOnSite.class);
    }

    @Test
    void rejects_a_snapshot_that_belongs_to_another_site() {
      PostSnapshot elegantSnapshot = aSnapshot().on(ELEGANT).build();

      assertThatThrownBy(() -> Post.publish(SOUNIE_WP, elegantSnapshot))
          .isInstanceOf(PostIdentityMismatch.class);
    }
  }

  @Nested
  class Revising {

    private final PostSnapshotBuilder stored =
        aSnapshot().sourcePostId("123").title("Old").tags("DDD", "Java").updatedAt(T1);

    @Test
    @DisplayName("AC-CAT-10: an identical snapshot is unchanged")
    void identical_snapshot_is_unchanged() {
      Post post = stored.buildStoredPost();

      Revision revision = post.revise(stored.build());

      assertThat(revision).isInstanceOf(Revision.Unchanged.class);
      assertThat(post.updatedAt()).isEqualTo(T1);
    }

    @Test
    @DisplayName("AC-CAT-11: a later snapshot with a new title raises PostRevised with TITLE")
    void later_snapshot_with_new_title_raises_PostRevised_with_only_TITLE_changed() {
      Post post = stored.buildStoredPost();
      PostSnapshot snapshot =
          aSnapshot().sourcePostId("123").title("New").tags("DDD", "Java").updatedAt(T2).build();

      Revision revision = post.revise(snapshot);

      assertThat(revision).isInstanceOf(Revision.Changed.class);
      PostRevised event = ((Revision.Changed) revision).event();
      assertThat(event.changed()).containsExactly(RevisedAspect.TITLE);
      assertThat(event.postId()).isEqualTo(post.id());
      assertThat(event.title()).isEqualTo(new Title("New"));
      assertThat(event.updatedAt()).isEqualTo(T2);
      assertThat(post.title()).isEqualTo(new Title("New"));
      assertThat(post.updatedAt()).isEqualTo(T2);
    }

    @Test
    void PostRevised_carries_the_full_new_state() {
      Post post = stored.buildStoredPost();
      PostSnapshot snapshot =
          aSnapshot()
              .sourcePostId("123")
              .title("New")
              .body("New body")
              .tags("DDD", "Java")
              .updatedAt(T2)
              .build();

      PostRevised event = ((Revision.Changed) post.revise(snapshot)).event();

      assertThat(event.url()).isEqualTo(snapshot.url());
      assertThat(event.body()).isEqualTo(new Body("New body"));
      assertThat(event.completeness()).isEqualTo(BodyCompleteness.FULL);
      assertThat(event.tags()).extracting(Tag::value).containsExactlyInAnyOrder("DDD", "Java");
      assertThat(event.publishedAt()).isEqualTo(snapshot.publishedAt());
      assertThat(event.changed())
          .containsExactlyInAnyOrder(RevisedAspect.TITLE, RevisedAspect.BODY);
    }

    @Test
    @DisplayName("AC-CAT-12: a newer timestamp without material change is Touched")
    void newer_timestamp_without_material_change_records_the_time_and_raises_no_event() {
      Post post = stored.buildStoredPost();

      Revision revision = post.revise(stored.updatedAt(T2).build());

      assertThat(revision).isInstanceOf(Revision.Touched.class);
      assertThat(post.updatedAt()).isEqualTo(T2);
    }

    @Test
    @DisplayName("AC-CAT-13: an older snapshot is Stale and ignored")
    void older_snapshot_is_stale_and_ignored() {
      Post post = stored.updatedAt(T2).buildStoredPost();
      PostSnapshot older =
          aSnapshot()
              .sourcePostId("123")
              .title("Different")
              .tags("DDD", "Java")
              .updatedAt(T1)
              .build();

      Revision revision = post.revise(older);

      assertThat(revision).isInstanceOf(Revision.Stale.class);
      assertThat(post.title()).isEqualTo(new Title("Old"));
      assertThat(post.updatedAt()).isEqualTo(T2);
    }

    @Test
    @DisplayName(
        "AC-CAT-14: upgrading a summary to the full body at the same updatedAt is a revision")
    void summary_upgraded_to_full_body_at_same_timestamp_is_a_revision() {
      PostSnapshotBuilder summary =
          aSnapshot()
              .on(ELEGANT)
              .sourcePostId("371460637286630063")
              .body("In March 2026 I got caught up")
              .completeness(BodyCompleteness.SUMMARY)
              .updatedAt(T1);
      Post post = summary.buildStoredPost();
      PostSnapshot full =
          summary
              .body("In March 2026 I got caught up in the reduction. And then much more.")
              .completeness(BodyCompleteness.FULL)
              .build();

      Revision revision = post.revise(full);

      assertThat(revision).isInstanceOf(Revision.Changed.class);
      assertThat(((Revision.Changed) revision).event().changed())
          .containsExactlyInAnyOrder(RevisedAspect.BODY, RevisedAspect.COMPLETENESS);
      assertThat(post.completeness()).isEqualTo(BodyCompleteness.FULL);
    }

    static Stream<Arguments> singleAspectChanges() {
      return Stream.of(
          Arguments.of(
              RevisedAspect.BODY, (UnaryOperator<PostSnapshotBuilder>) b -> b.body("Other body")),
          Arguments.of(
              RevisedAspect.COMPLETENESS,
              (UnaryOperator<PostSnapshotBuilder>) b -> b.completeness(BodyCompleteness.SUMMARY)),
          Arguments.of(RevisedAspect.TAGS, (UnaryOperator<PostSnapshotBuilder>) b -> b.tags("DDD")),
          Arguments.of(
              RevisedAspect.URL,
              (UnaryOperator<PostSnapshotBuilder>) b -> b.url("https://blog2.sounie.nz/renamed/")),
          Arguments.of(
              RevisedAspect.PUBLISHED_AT,
              (UnaryOperator<PostSnapshotBuilder>)
                  b -> b.publishedAt(PostSnapshotBuilder.DEFAULT_PUBLISHED_AT.minusSeconds(1))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("singleAspectChanges")
    void reports_exactly_the_aspect_that_changed(
        RevisedAspect aspect, UnaryOperator<PostSnapshotBuilder> change) {
      Post post = stored.buildStoredPost();
      PostSnapshot snapshot =
          change
              .apply(aSnapshot().sourcePostId("123").title("Old").tags("DDD", "Java"))
              .updatedAt(T2)
              .build();

      Revision revision = post.revise(snapshot);

      assertThat(revision).isInstanceOf(Revision.Changed.class);
      assertThat(((Revision.Changed) revision).event().changed()).containsExactly(aspect);
    }

    @Test
    void tags_differing_only_in_order_and_case_are_not_a_change() {
      Post post = stored.buildStoredPost();
      PostSnapshot snapshot =
          aSnapshot().sourcePostId("123").title("Old").tags("java", "ddd").updatedAt(T1).build();

      assertThat(post.revise(snapshot)).isInstanceOf(Revision.Unchanged.class);
    }

    @Test
    void rejects_a_snapshot_of_another_post() {
      Post post = stored.buildStoredPost();
      PostSnapshot other = aSnapshot().sourcePostId("456").updatedAt(T2).build();

      assertThatThrownBy(() -> post.revise(other)).isInstanceOf(PostIdentityMismatch.class);
    }
  }

  @Nested
  class Withdrawing {

    @Test
    void withdrawing_raises_PostWithdrawn_with_the_reason() {
      Post post =
          aSnapshot().sourcePostId("2").url("https://blog2.sounie.nz/two/").buildStoredPost();

      PostWithdrawn event = post.withdraw(WithdrawalReason.NO_LONGER_LISTED);

      assertThat(event.postId()).isEqualTo(post.id());
      assertThat(event.siteId()).isEqualTo(SOUNIE_WP.id());
      assertThat(event.url()).isEqualTo(post.url());
      assertThat(event.reason()).isEqualTo(WithdrawalReason.NO_LONGER_LISTED);
    }
  }
}
