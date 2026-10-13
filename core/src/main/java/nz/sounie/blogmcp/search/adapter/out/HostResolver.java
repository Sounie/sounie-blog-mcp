package nz.sounie.blogmcp.search.adapter.out;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Resolves a host name to its addresses, for the blog-category check of {@link HttpImageSource}
 * (search.md 3.7). An adapter-only seam we own; the HTTP client still connects through the system
 * resolver.
 */
@FunctionalInterface
interface HostResolver {

  /**
   * Every address of the host; an IP literal resolves to itself without DNS.
   *
   * @throws UnknownHostException if the host does not resolve
   */
  List<InetAddress> resolve(String host) throws UnknownHostException;

  /** The system resolver ({@link InetAddress#getAllByName}). */
  static HostResolver system() {
    return host -> List.of(InetAddress.getAllByName(host));
  }
}
