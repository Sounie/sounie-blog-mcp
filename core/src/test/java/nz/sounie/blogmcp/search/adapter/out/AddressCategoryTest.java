package nz.sounie.blogmcp.search.adapter.out;

import static nz.sounie.blogmcp.search.adapter.out.AddressCategory.LOOPBACK;
import static nz.sounie.blogmcp.search.adapter.out.AddressCategory.NON_ROUTABLE;
import static nz.sounie.blogmcp.search.adapter.out.AddressCategory.PRIVATE_NETWORK;
import static nz.sounie.blogmcp.search.adapter.out.AddressCategory.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/** AC-SRCH-78: the categorisation rule table and the permits table, on the enum itself. */
class AddressCategoryTest {

  @ParameterizedTest(name = "{0} is {1}")
  @CsvSource({
    "127.0.0.1, LOOPBACK",
    "127.255.0.9, LOOPBACK",
    "::1, LOOPBACK",
    "10.1.2.3, PRIVATE_NETWORK",
    "172.16.0.1, PRIVATE_NETWORK",
    "172.31.255.255, PRIVATE_NETWORK",
    "192.168.1.1, PRIVATE_NETWORK",
    "100.64.0.1, PRIVATE_NETWORK",
    "100.127.255.255, PRIVATE_NETWORK",
    "fd00::1, PRIVATE_NETWORK",
    "fc00::1, PRIVATE_NETWORK",
    "::ffff:10.1.2.3, PRIVATE_NETWORK",
    "::ffff:127.0.0.1, LOOPBACK",
    "::ffff:169.254.169.254, NON_ROUTABLE",
    "169.254.169.254, NON_ROUTABLE",
    "fe80::1, NON_ROUTABLE",
    "0.0.0.0, NON_ROUTABLE",
    "::, NON_ROUTABLE",
    "224.0.0.1, NON_ROUTABLE",
    "ff02::1, NON_ROUTABLE",
    "93.184.216.34, PUBLIC",
    "2606:4700::1111, PUBLIC",
    "172.32.0.1, PUBLIC",
    "100.128.0.1, PUBLIC",
    "11.0.0.1, PUBLIC"
  })
  @DisplayName("AC-SRCH-78: AddressCategory.of categorises addresses, IPv4-mapped forms included")
  void categorises_addresses(String address, AddressCategory category) {
    assertThat(AddressCategory.of(FixedHostResolver.literal(address))).isEqualTo(category);
  }

  static Stream<Arguments> permitsTable() {
    return Stream.of(
        Arguments.of(PUBLIC, EnumSet.of(PUBLIC)),
        Arguments.of(PRIVATE_NETWORK, EnumSet.of(PUBLIC, PRIVATE_NETWORK)),
        Arguments.of(LOOPBACK, EnumSet.of(PUBLIC, PRIVATE_NETWORK, LOOPBACK)),
        // Owner decision (2026-10-14): only a non-routable blog permits non-routable images.
        Arguments.of(NON_ROUTABLE, EnumSet.of(PUBLIC, NON_ROUTABLE)));
  }

  @ParameterizedTest(name = "a {0} blog permits {1}")
  @MethodSource("permitsTable")
  @DisplayName("AC-SRCH-78: each blog category permits exactly its image categories")
  void permits_exactly_the_table(AddressCategory blog, Set<AddressCategory> permitted) {
    Set<AddressCategory> actual = EnumSet.noneOf(AddressCategory.class);
    Stream.of(AddressCategory.values()).filter(blog::permits).forEach(actual::add);

    assertThat(actual).isEqualTo(permitted);
  }
}
