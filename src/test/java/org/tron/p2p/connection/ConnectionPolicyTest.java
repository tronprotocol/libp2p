package org.tron.p2p.connection;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.socket.MessageHandler;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.discover.Node;

public class ConnectionPolicyTest {

  @After
  public void clearPolicy() {
    ConnectionPolicy.replaceBlockedIps(Collections.emptySet());
  }

  @Test
  public void replaceBlockedIpsUsesDefensiveSnapshot() throws Exception {
    InetAddress blocked = InetAddress.getByName("192.0.2.1");
    Set<InetAddress> input = new HashSet<>();
    input.add(blocked);

    ConnectionPolicy.replaceBlockedIps(input);
    input.clear();

    Assert.assertTrue(ConnectionPolicy.isBlocked(blocked));
    Assert.assertTrue(ConnectionPolicy.isBlocked(new InetSocketAddress(blocked, 18888)));
  }

  @Test
  public void ipv4MappedIpv6MatchesIpv4() throws Exception {
    byte[] mappedBytes = new byte[16];
    mappedBytes[10] = (byte) 0xff;
    mappedBytes[11] = (byte) 0xff;
    mappedBytes[12] = (byte) 192;
    mappedBytes[14] = 2;
    mappedBytes[15] = 1;
    InetAddress mapped = Inet6Address.getByAddress(null, mappedBytes, -1);

    ConnectionPolicy.replaceBlockedIps(Collections.singleton(mapped));

    Assert.assertTrue(ConnectionPolicy.isBlocked(InetAddress.getByName("192.0.2.1")));
  }

  @Test
  public void rejectInvalidReplacement() throws Exception {
    try {
      ConnectionPolicy.replaceBlockedIps(null);
      Assert.fail("Expected null set to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("must not be null"));
    }

    Set<InetAddress> withNull = new HashSet<>();
    withNull.add(InetAddress.getByName("192.0.2.1"));
    withNull.add(null);
    try {
      ConnectionPolicy.replaceBlockedIps(withNull);
      Assert.fail("Expected null element to be rejected");
    } catch (IllegalArgumentException expected) {
      Assert.assertTrue(expected.getMessage().contains("must not contain null"));
    }
  }

  @Test
  public void peerClientRejectsBlockedAddressBeforeBootstrap() throws Exception {
    P2pConfig previousConfig = Parameter.p2pConfig;
    boolean previousShutdown = ChannelManager.isShutdown;
    InetSocketAddress address = new InetSocketAddress("192.0.2.40", 18888);
    try {
      Parameter.p2pConfig = new P2pConfig();
      ChannelManager.isShutdown = false;
      ConnectionPolicy.replaceBlockedIps(Collections.singleton(address.getAddress()));

      Assert.assertNull(new PeerClient().connectAsync(new Node(address), false));
    } finally {
      Parameter.p2pConfig = previousConfig;
      ChannelManager.isShutdown = previousShutdown;
    }
  }

  @Test
  public void messageHandlerClosesBlockedTcpChannelBeforeHandshake() {
    Parameter.p2pConfig = new P2pConfig();
    final InetSocketAddress address = new InetSocketAddress("192.0.2.41", 18888);
    ConnectionPolicy.replaceBlockedIps(Collections.singleton(address.getAddress()));
    Channel channel = new Channel();
    EmbeddedChannel embeddedChannel = new EmbeddedChannel() {
      @Override
      protected SocketAddress remoteAddress0() {
        return address;
      }
    };

    Assert.assertTrue(embeddedChannel.isOpen());
    embeddedChannel.pipeline().addLast(new MessageHandler(channel));
    embeddedChannel.pipeline().fireChannelActive();

    Assert.assertFalse(embeddedChannel.isOpen());
  }
}
