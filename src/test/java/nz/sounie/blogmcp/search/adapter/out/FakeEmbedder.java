package nz.sounie.blogmcp.search.adapter.out;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import nz.sounie.blogmcp.search.domain.Embedder;
import nz.sounie.blogmcp.search.domain.EmbedderUnavailable;
import nz.sounie.blogmcp.search.domain.Embedding;
import nz.sounie.blogmcp.search.domain.Passage;
import nz.sounie.blogmcp.search.domain.QueryPassage;

/**
 * Deterministic fake of our {@link Embedder} port. By default each text becomes a bag-of-words
 * vector: every lower-cased word is hashed into one of 384 dimensions, and the result is
 * normalised. Tests can assign exact vectors to passage texts or queries, make it fail, and inspect
 * every call.
 */
public final class FakeEmbedder implements Embedder {

  private final String modelId;
  private final List<List<Passage>> passageCalls = new CopyOnWriteArrayList<>();
  private final List<QueryPassage> queries = new CopyOnWriteArrayList<>();
  private final Map<String, Embedding> assignedPassages = new ConcurrentHashMap<>();
  private volatile Embedding assignedQuery;
  private volatile Predicate<List<Passage>> failWhen = passages -> false;

  public FakeEmbedder() {
    this("fake-model");
  }

  public FakeEmbedder(String modelId) {
    this.modelId = modelId;
  }

  @Override
  public String modelId() {
    return modelId;
  }

  @Override
  public List<Embedding> embedPassages(List<Passage> passages) {
    passageCalls.add(List.copyOf(passages));
    if (failWhen.test(passages)) {
      throw new EmbedderUnavailable("fake embedder failure");
    }
    return passages.stream()
        .map(p -> assignedPassages.getOrDefault(p.text(), bagOfWords(p.text())))
        .toList();
  }

  @Override
  public Embedding embedQuery(QueryPassage query) {
    queries.add(query);
    if (assignedQuery != null) {
      return assignedQuery;
    }
    return bagOfWords(query.text());
  }

  /** Returns this vector for a passage with exactly this text. */
  public FakeEmbedder assign(String passageText, Embedding embedding) {
    assignedPassages.put(passageText, embedding);
    return this;
  }

  /** Returns this vector for every query. */
  public FakeEmbedder answerQueriesWith(Embedding embedding) {
    this.assignedQuery = embedding;
    return this;
  }

  /** Fails every {@code embedPassages} call. */
  public FakeEmbedder failing() {
    return failWhen(passages -> true);
  }

  /** Fails {@code embedPassages} calls with any passage containing this text. */
  public FakeEmbedder failingForPassagesContaining(String marker) {
    return failWhen(passages -> passages.stream().anyMatch(p -> p.text().contains(marker)));
  }

  public FakeEmbedder working() {
    return failWhen(passages -> false);
  }

  private FakeEmbedder failWhen(Predicate<List<Passage>> condition) {
    this.failWhen = condition;
    return this;
  }

  public List<List<Passage>> passageCalls() {
    return List.copyOf(passageCalls);
  }

  public List<Passage> allPassages() {
    return passageCalls.stream().flatMap(List::stream).toList();
  }

  public List<QueryPassage> queries() {
    return List.copyOf(queries);
  }

  public int passageCallCount() {
    return passageCalls.size();
  }

  public void clearCalls() {
    passageCalls.clear();
    queries.clear();
  }

  /** A deterministic, unit-length vector from the words of the text. */
  public static Embedding bagOfWords(String text) {
    float[] v = new float[Embedding.DIMENSION];
    for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
      if (!word.isEmpty()) {
        v[Math.floorMod(word.hashCode(), Embedding.DIMENSION)] += 1f;
      }
    }
    v[Embedding.DIMENSION - 1] += 0.01f; // never the zero vector
    return new Embedding(normalised(v));
  }

  static float[] normalised(float[] v) {
    double sum = 0;
    for (float x : v) {
      sum += (double) x * x;
    }
    float norm = (float) Math.sqrt(sum);
    float[] out = new float[v.length];
    for (int i = 0; i < v.length; i++) {
      out[i] = v[i] / norm;
    }
    return out;
  }
}
