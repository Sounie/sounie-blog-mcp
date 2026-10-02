package nz.sounie.blogmcp.catalog.domain.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PostContentTest {

  private static final PostContent CURRENT =
      new PostContent(
          new CanonicalUrl(URI.create("https://blog2.sounie.nz/a/")),
          new Title("Title"),
          new Body("Body"),
          BodyCompleteness.FULL,
          tags("DDD", "Java"),
          Instant.parse("2026-09-20T01:20:47Z"));

  private static Set<Tag> tags(String... labels) {
    return new LinkedHashSet<>(Stream.of(labels).map(Tag::new).toList());
  }

  private static PostContent with(
      CanonicalUrl url,
      Title title,
      Body body,
      BodyCompleteness completeness,
      Set<Tag> tags,
      Instant publishedAt) {
    return new PostContent(url, title, body, completeness, tags, publishedAt);
  }

  static Stream<Arguments> singleChanges() {
    PostContent c = CURRENT;
    return Stream.of(
        Arguments.of(
            RevisedAspect.URL,
            (UnaryOperator<PostContent>)
                x ->
                    with(
                        new CanonicalUrl(URI.create("https://blog2.sounie.nz/b/")),
                        c.title(),
                        c.body(),
                        c.completeness(),
                        c.tags(),
                        c.publishedAt())),
        Arguments.of(
            RevisedAspect.TITLE,
            (UnaryOperator<PostContent>)
                x ->
                    with(
                        c.url(),
                        new Title("Other"),
                        c.body(),
                        c.completeness(),
                        c.tags(),
                        c.publishedAt())),
        Arguments.of(
            RevisedAspect.BODY,
            (UnaryOperator<PostContent>)
                x ->
                    with(
                        c.url(),
                        c.title(),
                        new Body("Other"),
                        c.completeness(),
                        c.tags(),
                        c.publishedAt())),
        Arguments.of(
            RevisedAspect.COMPLETENESS,
            (UnaryOperator<PostContent>)
                x ->
                    with(
                        c.url(),
                        c.title(),
                        c.body(),
                        BodyCompleteness.SUMMARY,
                        c.tags(),
                        c.publishedAt())),
        Arguments.of(
            RevisedAspect.TAGS,
            (UnaryOperator<PostContent>)
                x ->
                    with(
                        c.url(),
                        c.title(),
                        c.body(),
                        c.completeness(),
                        tags("DDD"),
                        c.publishedAt())),
        Arguments.of(
            RevisedAspect.PUBLISHED_AT,
            (UnaryOperator<PostContent>)
                x ->
                    with(
                        c.url(),
                        c.title(),
                        c.body(),
                        c.completeness(),
                        c.tags(),
                        c.publishedAt().minusSeconds(1))));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("singleChanges")
  void reports_each_aspect_that_changed_on_its_own(
      RevisedAspect aspect, UnaryOperator<PostContent> change) {
    assertThat(CURRENT.changedAspects(change.apply(CURRENT))).containsExactly(aspect);
  }

  @Test
  void identical_content_has_no_changed_aspects() {
    PostContent same =
        with(
            CURRENT.url(),
            CURRENT.title(),
            CURRENT.body(),
            CURRENT.completeness(),
            tags("DDD", "Java"),
            CURRENT.publishedAt());

    assertThat(CURRENT.changedAspects(same)).isEmpty();
  }

  @Test
  void reports_several_aspects_together() {
    PostContent other =
        with(
            CURRENT.url(),
            new Title("Other"),
            new Body("Other"),
            BodyCompleteness.SUMMARY,
            CURRENT.tags(),
            CURRENT.publishedAt());

    assertThat(CURRENT.changedAspects(other))
        .containsExactlyInAnyOrder(
            RevisedAspect.TITLE, RevisedAspect.BODY, RevisedAspect.COMPLETENESS);
  }

  @Test
  void tags_differing_only_in_order_and_case_are_not_a_change() {
    PostContent reordered =
        with(
            CURRENT.url(),
            CURRENT.title(),
            CURRENT.body(),
            CURRENT.completeness(),
            tags("java", "ddd"),
            CURRENT.publishedAt());

    assertThat(CURRENT.changedAspects(reordered)).isEmpty();
  }

  @Test
  void tags_are_an_unmodifiable_copy() {
    Set<Tag> source = tags("DDD");
    PostContent content =
        with(
            CURRENT.url(),
            CURRENT.title(),
            CURRENT.body(),
            CURRENT.completeness(),
            source,
            CURRENT.publishedAt());

    source.add(new Tag("Java"));

    assertThat(content.tags()).extracting(Tag::value).containsExactly("DDD");
  }
}
