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
 * Collects a response body of at most {@code capacity} bytes. One byte more cancels the exchange
 * and completes with empty, so an oversized body is never read in full. A capacity of 0 discards
 * any non-empty body at once.
 */
final class CappedBody implements HttpResponse.BodySubscriber<Optional<byte[]>> {

  private final int capacity;
  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final CompletableFuture<Optional<byte[]>> body = new CompletableFuture<>();
  private Flow.Subscription subscription;

  CappedBody(int capacity) {
    this.capacity = capacity;
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
      // Complete first: the client fails the response if the cancellation is seen before the body.
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
