package nz.sounie.blogmcp.search.adapter.out;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/**
 * Where an address lives, and which image addresses a blog in each category may use (search.md
 * 3.7). The JDK already turns an IPv4-mapped IPv6 address ({@code ::ffff:a.b.c.d}) into its {@code
 * Inet4Address}, so the IPv4 rules cover those forms too.
 */
enum AddressCategory {
  /** Every address not in a category below. A public blog permits only public images. */
  PUBLIC {
    @Override
    Set<AddressCategory> permitted() {
      return EnumSet.of(PUBLIC);
    }
  },
  /** 10/8, 172.16/12, 192.168/16, 100.64/10 (CGNAT, e.g. Tailscale) and fc00::/7. */
  PRIVATE_NETWORK {
    @Override
    Set<AddressCategory> permitted() {
      return EnumSet.of(PUBLIC, PRIVATE_NETWORK);
    }
  },
  /** 127/8 and ::1. */
  LOOPBACK {
    @Override
    Set<AddressCategory> permitted() {
      return EnumSet.of(PUBLIC, PRIVATE_NETWORK, LOOPBACK);
    }
  },
  /**
   * Link-local (169.254/16, fe80::/10), any-local and multicast. Only a non-routable blog permits
   * non-routable images (owner decision, 2026-10-14).
   */
  NON_ROUTABLE {
    @Override
    Set<AddressCategory> permitted() {
      return EnumSet.of(PUBLIC, NON_ROUTABLE);
    }
  };

  /** The image address categories a blog in this category may use. */
  abstract Set<AddressCategory> permitted();

  /** Whether a blog in this category may fetch an image from an address in that category. */
  boolean permits(AddressCategory image) {
    return permitted().contains(image);
  }

  /** The category of the address: the first rule that matches, else {@link #PUBLIC}. */
  static AddressCategory of(InetAddress address) {
    return Rules.TABLE.stream()
        .filter(rule -> rule.matches().test(address))
        .map(Rule::category)
        .findFirst()
        .orElse(PUBLIC);
  }

  private record Rule(Predicate<InetAddress> matches, AddressCategory category) {}

  /** Holds the rule table, which refers to the constants above. */
  private static final class Rules {
    static final List<Rule> TABLE =
        List.of(
            new Rule(InetAddress::isLoopbackAddress, LOOPBACK),
            new Rule(InetAddress::isLinkLocalAddress, NON_ROUTABLE),
            new Rule(InetAddress::isAnyLocalAddress, NON_ROUTABLE),
            new Rule(InetAddress::isMulticastAddress, NON_ROUTABLE),
            new Rule(AddressBlock.parse("10.0.0.0", 8)::contains, PRIVATE_NETWORK),
            new Rule(AddressBlock.parse("172.16.0.0", 12)::contains, PRIVATE_NETWORK),
            new Rule(AddressBlock.parse("192.168.0.0", 16)::contains, PRIVATE_NETWORK),
            new Rule(AddressBlock.parse("100.64.0.0", 10)::contains, PRIVATE_NETWORK),
            new Rule(AddressBlock.parse("fc00::", 7)::contains, PRIVATE_NETWORK));
  }

  /** A CIDR block: the addresses of the same family whose first bits equal the network's. */
  private record AddressBlock(byte[] network, int prefixBits) {

    /** An IP literal never makes {@link InetAddress#getByName} query DNS. */
    static AddressBlock parse(String networkLiteral, int prefixBits) {
      try {
        return new AddressBlock(InetAddress.getByName(networkLiteral).getAddress(), prefixBits);
      } catch (UnknownHostException e) {
        throw new IllegalArgumentException("Not an IP literal: " + networkLiteral, e);
      }
    }

    boolean contains(InetAddress address) {
      byte[] bytes = address.getAddress();
      return bytes.length == network.length
          && IntStream.range(0, prefixBits)
              .allMatch(bit -> bitAt(bytes, bit) == bitAt(network, bit));
    }

    private static int bitAt(byte[] bytes, int bit) {
      return (bytes[bit / Byte.SIZE] >> (Byte.SIZE - 1 - bit % Byte.SIZE)) & 1;
    }
  }
}
