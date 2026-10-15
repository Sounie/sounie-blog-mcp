package nz.sounie.blogmcp.search.adapter.out;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/**
 * A fixed-answer implementation of our own {@link HostResolver} seam (search.md 3.7): each host
 * name resolves to the addresses the test gives it. An IP literal resolves to itself (no DNS); any
 * other unknown name does not resolve.
 *
 * <p>The answer is only what {@code HttpImageSource} checks against the blog category. The HTTP
 * client still connects through the system resolver, so a test can make {@code localhost} (where
 * WireMock listens) look like a public host.
 */
final class FixedHostResolver implements HostResolver {

  private final Map<String, List<InetAddress>> answers = new HashMap<>();
  private final List<String> resolved = new CopyOnWriteArrayList<>();

  /** {@code host} resolves to these address literals. */
  FixedHostResolver resolving(String host, String... addresses) {
    answers.put(host, Stream.of(addresses).map(FixedHostResolver::literal).toList());
    return this;
  }

  @Override
  public List<InetAddress> resolve(String host) throws UnknownHostException {
    resolved.add(host);
    List<InetAddress> answer = answers.get(host);
    if (answer != null) {
      return answer;
    }
    if (isLiteral(host)) {
      return List.of(literal(host));
    }
    throw new UnknownHostException(host + " (not known to the test resolver)");
  }

  /** Every host asked for, in order. */
  List<String> resolvedHosts() {
    return List.copyOf(resolved);
  }

  private static boolean isLiteral(String host) {
    return host.matches("[0-9.]+") || host.contains(":") || host.startsWith("[");
  }

  /** Parses an IP literal; {@link InetAddress#getByName} never queries DNS for one. */
  static InetAddress literal(String address) {
    try {
      return InetAddress.getByName(address);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("Not an IP literal: " + address, e);
    }
  }
}
