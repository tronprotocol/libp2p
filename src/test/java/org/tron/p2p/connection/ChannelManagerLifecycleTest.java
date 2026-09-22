package org.tron.p2p.connection;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.business.handshake.HandshakeService;
import org.tron.p2p.connection.business.pool.ConnPoolService;
import org.tron.p2p.connection.message.detect.StatusMessage;
import org.tron.p2p.connection.message.handshake.HelloMessage;
import org.tron.p2p.protos.Connect;
import org.tron.p2p.protos.Discover;

public class ChannelManagerLifecycleTest {
  private static final InetSocketAddress REMOTE = new InetSocketAddress("127.0.0.2", 18888);
  private final long startTime = System.currentTimeMillis() - 10000;
  private final List<EmbeddedChannel> sockets = new ArrayList<>();
  private P2pConfig previousConfig;
  private List<P2pEventHandler> previousHandlers;
  private ConnPoolService pool;
  private HandshakeService handshake;

  @Before
  public void setUp() {
    previousConfig = Parameter.p2pConfig;
    previousHandlers = Parameter.handlerList;
    Parameter.p2pConfig = new P2pConfig();
    Parameter.p2pConfig.setNetworkId(11111);
    Parameter.p2pConfig.setMaxConnectionsWithSameIp(10);
    Parameter.handlerList = new ArrayList<>();
    ChannelManager.getAllChannels().forEach(ChannelManager::notifyDisconnect);
    ChannelManager.getBannedNodes().invalidateAll();
    pool = new ConnPoolService();
    handshake = new HandshakeService();
  }

  @After
  public void tearDown() {
    sockets.forEach(EmbeddedChannel::finishAndReleaseAll);
    pool.close();
    ChannelManager.getAllChannels().forEach(ChannelManager::notifyDisconnect);
    ChannelManager.getBannedNodes().invalidateAll();
    Parameter.handlerList = previousHandlers;
    Parameter.p2pConfig = previousConfig;
  }

  @Test
  public void sameAddressHandshakesRemainRegisteredInBothCloseOrders() throws Exception {
    for (boolean firstActive : new boolean[]{false, true}) {
      for (boolean closeFirst : new boolean[]{false, true}) {
        ChannelManager.getBannedNodes().invalidateAll();
        Channel first = channel(firstActive, 1);
        Channel second = channel(!firstActive, 2);
        hello(first, 1);
        hello(second, 2);
        Assert.assertNotSame(first, second);
        Assert.assertEquals(first, second);
        Assert.assertTrue(first.isFinishHandshake());
        Assert.assertTrue(second.isFinishHandshake());
        assertRegistered(first, second);
        assertCounts(1, 1);

        Channel closed = closeFirst ? first : second;
        Channel survivor = closeFirst ? second : first;
        closed.close();
        Assert.assertTrue(survivor.getCtx().channel().isOpen());
        assertRegistered(survivor);
        assertCounts(survivor.isActive() ? 1 : 0, survivor.isActive() ? 0 : 1);

        ChannelManager.notifyDisconnect(closed);
        assertRegistered(survivor);
        assertCounts(survivor.isActive() ? 1 : 0, survivor.isActive() ? 0 : 1);
        survivor.close();
        assertRegistered();
        assertCounts(0, 0);
      }
    }
  }

  @Test
  public void rejectedDuplicateHandshakeKeepsEstablishedChannel() throws Exception {
    Channel established = channel(true, 1);
    Channel rejected = channel(false, 2);
    hello(established, 1);
    hello(rejected, 1);
    Assert.assertTrue(established.isFinishHandshake());
    Assert.assertTrue(established.getCtx().channel().isOpen());
    Assert.assertFalse(rejected.isFinishHandshake());
    Assert.assertTrue(rejected.isDisconnect());
    assertRegistered(established);
    assertCounts(1, 0);
  }

  @Test
  public void lateCloseNotificationKeepsSameNodeReplacement() throws Exception {
    Channel newer = channel(true, 2);
    Channel earlier = channel(false, 1);
    hello(newer, 1);
    hello(earlier, 1);
    Assert.assertTrue(newer.isDisconnect());
    Assert.assertTrue(earlier.isFinishHandshake());
    Assert.assertTrue(earlier.getCtx().channel().isOpen());
    assertRegistered(earlier);
    assertCounts(0, 1);

    ChannelManager.notifyDisconnect(newer);
    assertRegistered(earlier);
    assertCounts(0, 1);
  }

  @Test
  public void unregisteredSameAddressCloseKeepsEstablishedChannel() throws Exception {
    Channel established = channel(true, 1);
    hello(established, 1);
    Channel unregistered = channel(false, 2);
    unregistered.close();
    ChannelManager.notifyDisconnect(unregistered);
    assertRegistered(established);
    assertCounts(1, 0);
  }

  @Test
  public void repeatedAdmissionOfSameInstanceDoesNotDoubleCount() throws Exception {
    Channel established = channel(false, 1);
    hello(established, 1);
    Assert.assertEquals(DisconnectCode.NORMAL, ChannelManager.processPeer(established));
    assertRegistered(established);
    assertCounts(0, 1);
  }

  @Test
  public void connectionLimitsCountAllInstancesAtSameAddress() throws Exception {
    Channel first = channel(false, 1);
    Channel second = channel(true, 2);
    hello(first, 1);
    hello(second, 2);
    Channel candidate = channel(false, 3);
    candidate.setNodeId("third-node");

    Parameter.p2pConfig.setMaxConnections(2);
    Assert.assertEquals(DisconnectCode.TOO_MANY_PEERS, ChannelManager.processPeer(candidate));
    Parameter.p2pConfig.setMaxConnections(3);
    Parameter.p2pConfig.setMaxConnectionsWithSameIp(2);
    Assert.assertEquals(DisconnectCode.MAX_CONNECTION_WITH_SAME_IP,
        ChannelManager.processPeer(candidate));
    assertRegistered(first, second);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void snapshotsAreDetachedAndStatusCountsInstances() throws Exception {
    Channel first = channel(false, 1);
    Channel second = channel(true, 2);
    hello(first, 1);
    hello(second, 2);

    Connect.StatusMessage status = Connect.StatusMessage.parseFrom(new StatusMessage().getData());
    Assert.assertEquals(2, status.getCurrentConnections());
    List<Channel> snapshot = ChannelManager.getAllChannels();
    Map<InetSocketAddress, Channel> addresses = ChannelManager.getChannels();
    Assert.assertEquals(1, addresses.size());
    Assert.assertSame(second, addresses.get(REMOTE));
    addresses.clear();
    snapshot.clear();
    assertRegistered(first, second);

    second.close();
    Assert.assertSame(first, ChannelManager.getChannels().get(REMOTE));
    status = Connect.StatusMessage.parseFrom(new StatusMessage().getData());
    Assert.assertEquals(1, status.getCurrentConnections());
  }

  private Channel channel(boolean active, long order) throws Exception {
    Channel channel = new Channel();
    EmbeddedChannel socket = new EmbeddedChannel(new ChannelInboundHandlerAdapter());
    sockets.add(socket);
    set(channel, "inetSocketAddress", REMOTE);
    set(channel, "inetAddress", REMOTE.getAddress());
    set(channel, "isActive", active);
    set(channel, "startTime", startTime + order);
    set(channel, "ctx", socket.pipeline().firstContext());
    // Mirror P2pChannelInitializer's normal-channel close callback without opening real sockets.
    socket.closeFuture().addListener(future -> {
      channel.setDisconnect(true);
      ChannelManager.notifyDisconnect(channel);
    });
    return channel;
  }

  private void hello(Channel channel, int idByte) throws Exception {
    byte[] id = new byte[64];
    Arrays.fill(id, (byte) idByte);
    Discover.Endpoint endpoint = Discover.Endpoint.newBuilder()
        .setNodeId(ByteString.copyFrom(id)).setAddress(ByteString.copyFromUtf8("127.0.0.2"))
        .setPort(18888).build();
    Connect.HelloMessage hello = Connect.HelloMessage.newBuilder().setFrom(endpoint)
        .setNetworkId(11111).setVersion(1).setTimestamp(System.currentTimeMillis()).build();
    handshake.processMessage(channel, new HelloMessage(hello.toByteArray()));
  }

  private void assertRegistered(Channel... expected) {
    List<Channel> actual = ChannelManager.getAllChannels();
    Assert.assertEquals(expected.length, actual.size());
    Assert.assertEquals(expected.length, ChannelManager.getChannelCount());
    Assert.assertEquals(expected.length, ChannelManager.getConnectionNum(REMOTE.getAddress()));
    for (Channel channel : expected) {
      Assert.assertTrue(actual.stream().anyMatch(registered -> registered == channel));
    }
  }

  private void assertCounts(int active, int passive) {
    Assert.assertEquals(active, pool.getActivePeersCount().get());
    Assert.assertEquals(passive, pool.getPassivePeersCount().get());
  }

  private static void set(Channel channel, String name, Object value) throws Exception {
    Field field = Channel.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(channel, value);
  }
}
