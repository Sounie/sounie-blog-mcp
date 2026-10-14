package nz.sounie.blogmcp.search.adapter.out;

import java.io.ByteArrayOutputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * Collects a response body of at most {@code capacity} bytes. One byte more cuts the body short: it
 * reports the cut, completes with empty and cancels the exchange, so an oversized body is never
 * read in full. A capacity of 0 discards any non-empty body at once.
 *
 * <p>The cut is reported before the cancellation because the client may then fail the whole
 * exchange instead of delivering the empty body; the caller answers from the cut instead.
 */
final class CappedBody implements HttpResponse.BodySubscriber<Optional<byte[]>> {

  private final int capacity;
  private final Runnable onCut;
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final CompletableFuture<Optional<byte[]>> body = new CompletableFuture<>();
  private Flow.Subscription subscription;

  /**
   * @param onCut run once the body is cut short, before the exchange is cancelled
   */
  CappedBody(int capacity, Runnable onCut) {
    this.capacity = capacity;
    this.onCut = onCut;
  }

  @Override
  public CompletionStage<Optional<byte[]>> getBody() {
    return body;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(Long.MAX_VALUE);
  }

  @Override
  public void onNext(List<ByteBuffer> buffers) {
    buffers.forEach(this::append);
    if (bytes.size() > capacity) {
      onCut.run();
      body.complete(Optional.empty());
      subscription.cancel();
    }
  }

  /** Keeps at most one byte over the capacity, however much arrives. */
  private void append(ByteBuffer buffer) {
    byte[] kept = new byte[Math.min(capacity + 1 - bytes.size(), buffer.remaining())];
    buffer.get(kept);
    bytes.writeBytes(kept);
  }

  @Override
  public void onError(Throwable failure) {
    body.completeExceptionally(failure);
  }

  @Override
  public void onComplete() {
    body.complete(Optional.of(bytes.toByteArray()));
  }
}
