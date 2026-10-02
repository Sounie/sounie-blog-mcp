package nz.sounie.blogmcp.search.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * One post in the catalog's listing, as far as search could read it (AC-SRCH-38). A post search
 * cannot translate is never mistaken for a withdrawn post.
 *
 * <p>Each variant answers for itself what it contributes to a reconcile plan, so the plan has no
 * branches on the variant.
 */
public sealed interface CatalogEntry {

  /** The reason given for a post ID that the listing holds more than once (review S2). */
  String DUPLICATE_REASON = "duplicate post ID in the catalog listing";

  /** The post ID, when search could read it. */
  Optional<PostId> knownId();

  /** The index decisions this entry plans against the snapshot under the recipe. */
  Stream<IndexDecision> decisions(VectorIndex snapshot, IndexRecipe recipe);

  /**
   * Why this entry stops orphans being removed in this run: the reason of an {@link Unidentified}
   * entry, which could be any indexed post; empty for the other variants.
   */
  Stream<String> orphanRemovalBlockers();

  /** This entry, or an {@link Unreadable} duplicate if its post ID is listed more than once. */
  default CatalogEntry listedOnce(Set<PostId> duplicated) {
    return knownId()
        .filter(duplicated::contains)
        .<CatalogEntry>map(id -> new Unreadable(id, DUPLICATE_REASON))
        .orElse(this);
  }

  /** Translated: goes through the normal index decision. */
  record Readable(PostToIndex post) implements CatalogEntry {
    public Readable {
      Objects.requireNonNull(post, "post");
    }

    @Override
    public Optional<PostId> knownId() {
      return Optional.of(post.id());
    }

    @Override
    public Stream<IndexDecision> decisions(VectorIndex snapshot, IndexRecipe recipe) {
      return Stream.of(
          IndexDecision.forPost(snapshot.find(post.id()), post, post.fingerprintUnder(recipe)));
    }

    @Override
    public Stream<String> orphanRemovalBlockers() {
      return Stream.empty();
    }
  }

  /** The post ID parses, but another field is malformed or missing: keep the entry, report it. */
  record Unreadable(PostId id, String reason) implements CatalogEntry {
    public Unreadable {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(reason, "reason");
    }

    @Override
    public Optional<PostId> knownId() {
      return Optional.of(id);
    }

    @Override
    public Stream<IndexDecision> decisions(VectorIndex snapshot, IndexRecipe recipe) {
      return Stream.of(new IndexDecision.Unreadable(id, reason));
    }

    @Override
    public Stream<String> orphanRemovalBlockers() {
      return Stream.empty();
    }
  }

  /** The post ID itself cannot be read: no orphan can be identified safely. */
  record Unidentified(String reason) implements CatalogEntry {
    public Unidentified {
      Objects.requireNonNull(reason, "reason");
    }

    @Override
    public Optional<PostId> knownId() {
      return Optional.empty();
    }

    @Override
    public Stream<IndexDecision> decisions(VectorIndex snapshot, IndexRecipe recipe) {
      return Stream.empty();
    }

    @Override
    public Stream<String> orphanRemovalBlockers() {
      return Stream.of(reason);
    }
  }
}
