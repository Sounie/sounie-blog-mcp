package nz.sounie.blogmcp.search.domain.index;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import nz.sounie.blogmcp.search.domain.embedding.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.embedding.ImageEmbedder;
import nz.sounie.blogmcp.search.domain.embedding.ImagePixels;
import nz.sounie.blogmcp.search.domain.image.ImageFetch;
import nz.sounie.blogmcp.search.domain.image.ImageSource;
import nz.sounie.blogmcp.search.domain.image.PostImage;

/**
 * The strategy for a post's images, chosen once at composition (search.md 3.7): {@link TextOnly}
 * when the provider cannot embed images, {@link WithImages} when it can. Nothing else asks which
 * one it is.
 */
public sealed interface ImageIndexing {

  /** No fetching; recipe and fingerprint exactly as before images were indexed. */
  static ImageIndexing textOnly() {
    return new TextOnly();
  }

  /** Fetch each post image from the source and embed it alone. */
  static ImageIndexing withImages(ImageSource source, ImageEmbedder embedder) {
    return new WithImages(source, embedder);
  }

  /** The recipe of entries built with this strategy from the text recipe. */
  IndexRecipe appliedTo(IndexRecipe textRecipe);

  /** The post images the current content fingerprint covers. */
  List<PostImage> fingerprinted(PostToIndex post);

  /** One attempt per post image, in catalog order. */
  List<ImageAttempt> attempt(PostToIndex post);

  /** For providers without an image embedder. */
  record TextOnly() implements ImageIndexing {

    @Override
    public IndexRecipe appliedTo(IndexRecipe textRecipe) {
      return textRecipe;
    }

    @Override
    public List<PostImage> fingerprinted(PostToIndex post) {
      return List.of();
    }

    @Override
    public List<ImageAttempt> attempt(PostToIndex post) {
      return List.of();
    }
  }

  /** For providers with an image embedder. */
  record WithImages(ImageSource source, ImageEmbedder embedder) implements ImageIndexing {

    /** The image part of the recipe. */
    public static final String RECIPE_PART = "images-v1";

    public WithImages {
      Objects.requireNonNull(source, "source");
      Objects.requireNonNull(embedder, "embedder");
    }

    @Override
    public IndexRecipe appliedTo(IndexRecipe textRecipe) {
      return new IndexRecipe(textRecipe.id() + "/" + RECIPE_PART);
    }

    @Override
    public List<PostImage> fingerprinted(PostToIndex post) {
      return post.images();
    }

    /** Images are fetched for the post's canonical URL and embedded one at a time. */
    @Override
    public List<ImageAttempt> attempt(PostToIndex post) {
      List<PostImage> images = post.images();
      URI postUrl = URI.create(post.metadata().canonicalUrl());
      return IntStream.range(0, images.size())
          .mapToObj(i -> attempt(i, images.get(i), postUrl))
          .toList();
    }

    private ImageAttempt attempt(int index, PostImage image, URI postUrl) {
      return switch (source.fetch(image.url(), postUrl)) {
        case ImageFetch.Fetched fetched -> embedded(index, image, fetched.pixels());
        case ImageFetch.Rejected rejected -> new ImageAttempt.Skipped(image);
        case ImageFetch.Unavailable unavailable -> new ImageAttempt.Pending(image);
      };
    }

    /** An embedding failure affects only this image, which stays pending. */
    private ImageAttempt embedded(int index, PostImage image, ImagePixels pixels) {
      try {
        IndexedImage indexed =
            new IndexedImage(index, image.url(), image.description(), embedder.embedImage(pixels));
        return new ImageAttempt.Indexed(image, indexed);
      } catch (EmbedderUnavailable e) {
        return new ImageAttempt.Pending(image);
      }
    }
  }
}
