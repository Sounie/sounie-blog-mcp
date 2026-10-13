package nz.sounie.blogmcp.catalog.application;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import nz.sounie.blogmcp.catalog.domain.post.PostEvent;
import nz.sounie.blogmcp.catalog.domain.post.PostImages;
import nz.sounie.blogmcp.catalog.domain.post.PostPublished;
import nz.sounie.blogmcp.catalog.domain.post.PostRevised;
import nz.sounie.blogmcp.catalog.domain.post.PostWithdrawn;
import nz.sounie.blogmcp.catalog.domain.post.RevisedAspect;
import nz.sounie.blogmcp.catalog.domain.post.Tag;
import nz.sounie.blogmcp.shared.event.CatalogPostPublished;
import nz.sounie.blogmcp.shared.event.CatalogPostRevised;
import nz.sounie.blogmcp.shared.event.CatalogPostWithdrawn;
import nz.sounie.blogmcp.shared.event.IntegrationEvent;
import nz.sounie.blogmcp.shared.query.CatalogPostImage;

/** Translates catalog domain events into the published language in {@code shared}. */
final class IntegrationEvents {

  private IntegrationEvents() {}

  static IntegrationEvent from(PostEvent event) {
    return switch (event) {
      case PostPublished published ->
          new CatalogPostPublished(
              published.postId().external(),
              published.siteId().value(),
              published.url().value().toString(),
              published.title().value(),
              published.body().text(),
              published.completeness().name(),
              names(published.tags(), Tag::value),
              images(published.images()),
              published.publishedAt(),
              published.updatedAt());
      case PostRevised revised ->
          new CatalogPostRevised(
              revised.postId().external(),
              revised.siteId().value(),
              revised.url().value().toString(),
              revised.title().value(),
              revised.body().text(),
              revised.completeness().name(),
              names(revised.tags(), Tag::value),
              images(revised.images()),
              revised.publishedAt(),
              revised.updatedAt(),
              names(revised.changed(), RevisedAspect::name));
      case PostWithdrawn withdrawn ->
          new CatalogPostWithdrawn(
              withdrawn.postId().external(),
              withdrawn.siteId().value(),
              withdrawn.url().value().toString(),
              withdrawn.reason().name());
    };
  }

  static <T> Set<String> names(Collection<T> items, Function<T, String> name) {
    Set<String> names = new LinkedHashSet<>();
    items.forEach(item -> names.add(name.apply(item)));
    return Collections.unmodifiableSet(names);
  }

  /** The post images in the published language, in document order. */
  static List<CatalogPostImage> images(PostImages images) {
    return images.asList().stream()
        .map(
            image -> new CatalogPostImage(image.url().toString(), image.altText(), image.caption()))
        .toList();
  }
}
