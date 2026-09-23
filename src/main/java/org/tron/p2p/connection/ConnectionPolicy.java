package org.tron.p2p.connection;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runtime connection policy shared by TCP connection paths.
 */
public final class ConnectionPolicy {

  private static final AtomicReference<Set<IpAddressKey>> BLOCKED_IPS =
      new AtomicReference<>(Collections.emptySet());

  private ConnectionPolicy() {
  }

  public static void replaceBlockedIps(Set<InetAddress> blockedIps) {
    if (blockedIps == null) {
      throw new IllegalArgumentException("blockedIps must not be null");
    }
    Set<IpAddressKey> replacement = new HashSet<>();
    for (InetAddress address : blockedIps) {
      if (address == null) {
        throw new IllegalArgumentException("blockedIps must not contain null");
      }
      replacement.add(IpAddressKey.from(address));
    }
    BLOCKED_IPS.set(Collections.unmodifiableSet(replacement));
  }

  public static boolean isBlocked(InetAddress address) {
    return address != null && BLOCKED_IPS.get().contains(IpAddressKey.from(address));
  }

  /**
   * Checks resolved endpoints used by internal TCP and connection-candidate paths. Blocking is
   * IP-based, so the endpoint port is ignored. UDP discovery traffic is intentionally outside the
   * first-phase blacklist scope. Null or unresolved endpoints return {@code false}; public
   * management APIs must validate their input separately.
   */
  public static boolean isBlocked(InetSocketAddress address) {
    return address != null && !address.isUnresolved() && isBlocked(address.getAddress());
  }

  /**
   * Immutable binary IP key used for blacklist snapshot lookup. It normalizes IPv4-mapped IPv6
   * addresses to IPv4 and excludes host names and ports, so equivalent representations of the
   * same IP cannot bypass the blacklist and the key's hash remains stable.
   */
  private static final class IpAddressKey {

    private final byte[] address;

    private IpAddressKey(byte[] address) {
      this.address = address;
    }

    private static IpAddressKey from(InetAddress address) {
      try {
        byte[] normalized = InetAddress.getByAddress(address.getAddress()).getAddress();
        return new IpAddressKey(normalized);
      } catch (UnknownHostException e) {
        throw new IllegalArgumentException("Unsupported IP address", e);
      }
    }

    @Override
    public boolean equals(Object object) {
      if (this == object) {
        return true;
      }
      if (!(object instanceof IpAddressKey)) {
        return false;
      }
      IpAddressKey that = (IpAddressKey) object;
      return Arrays.equals(address, that.address);
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(address);
    }
  }
}
