package org.tron.p2p.connection;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.pool.ConnPoolService;
import org.tron.p2p.discover.Node;

public class ConnPoolServiceTest {

  private static final String LOCAL_IP = "127.0.0.1";
  private static final int PORT = 10000;
  private static final InetSocketAddress REMOTE = new InetSocketAddress("127.0.0.2", 18888);
  private final List<Channel> channels = new ArrayList<>();
  private P2pConfig previousConfig;
  private List<P2pEventHandler> previousHandlers;
  private ConnPoolService pool;

  @Before
  public void setUp() {
    previousConfig = Parameter.p2pConfig;
    previousHandlers = Parameter.handlerList;
    Parameter.p2pConfig = new P2pConfig();
    Parameter.p2pConfig.setIp(LOCAL_IP);
    Parameter.p2pConfig.setIpv6("");
    Parameter.p2pConfig.setDiscoverEnable(false);
    Parameter.p2pConfig.setPort(PORT);
    Parameter.handlerList = new ArrayList<>();
    clearChannels();
    pool = new ConnPoolService();
  }

  @After
  public void tearDown() {
    try {
      channels.forEach(channel -> channel.setDisconnect(true));
      pool.close();
      clearChannels();
    } finally {
      Parameter.p2pConfig = previousConfig;
      Parameter.handlerList = previousHandlers;
    }
  }

  @Test
  public void getNodes_chooseHomeNode() {
    InetSocketAddress localAddress = new InetSocketAddress(Parameter.p2pConfig.getIp(),
        Parameter.p2pConfig.getPort());
    Set<InetSocketAddress> inetInUse = new HashSet<>();
    inetInUse.add(localAddress);

    List<Node> connectableNodes = new ArrayList<>();
    connectableNodes.add(new Node(Parameter.p2pConfig.getNodeID(),
        Parameter.p2pConfig.getIp(), Parameter.p2pConfig.getIpv6(), Parameter.p2pConfig.getPort()));

    List<Node> nodes = pool.getNodes(new HashSet<>(), inetInUse, connectableNodes,
        1);
    Assert.assertEquals(0, nodes.size());

    nodes = pool.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        1);
    Assert.assertEquals(1, nodes.size());
  }

  @Test
  public void getNodes_respectsLimit() {
    Node node1 = new Node(new InetSocketAddress(LOCAL_IP, 90));
    Node node2 = new Node(new InetSocketAddress(LOCAL_IP, 100));

    List<Node> connectableNodes = new ArrayList<>();
    connectableNodes.add(node1);
    connectableNodes.add(node2);

    List<Node> nodes = pool.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        2);
    Assert.assertEquals(2, nodes.size());
    // getNodes shuffles candidates, so compare their contents without assuming an order.
    Assert.assertEquals(new HashSet<>(connectableNodes), new HashSet<>(nodes));

    int limit = 1;
    List<Node> nodes2 = pool.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        limit);
    Assert.assertEquals(limit, nodes2.size());
    Assert.assertTrue(connectableNodes.containsAll(nodes2));
  }

  @Test
  public void getNodes_banNode() throws InterruptedException {
    InetSocketAddress inetSocketAddress = new InetSocketAddress(LOCAL_IP, 90);
    long banTime = 500L;
    ChannelManager.banNode(inetSocketAddress.getAddress(), banTime);
    Node node = new Node(inetSocketAddress);
    List<Node> connectableNodes = new ArrayList<>();
    connectableNodes.add(node);

    List<Node> nodes = pool.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        1);
    Assert.assertEquals(0, nodes.size());
    Thread.sleep(2 * banTime);

    nodes = pool.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes, 1);
    Assert.assertEquals(1, nodes.size());
  }

  @Test
  public void getNodes_nodeInUse() {
    InetSocketAddress inetSocketAddress = new InetSocketAddress(LOCAL_IP, 90);
    Node node = new Node(inetSocketAddress);
    List<Node> connectableNodes = new ArrayList<>();
    connectableNodes.add(node);

    Set<String> nodesInUse = new HashSet<>();
    nodesInUse.add(node.getHexId());
    List<Node> nodes = pool.getNodes(nodesInUse, new HashSet<>(), connectableNodes, 1);
    Assert.assertEquals(0, nodes.size());
  }

  @Test
  public void sameAddressPeersAreTrackedSeparatelyInEitherCloseOrder() throws Exception {
    for (boolean firstActive : new boolean[]{false, true}) {
      for (boolean closeFirst : new boolean[]{false, true}) {
        Channel first = channel(firstActive);
        Channel second = channel(!firstActive);
        Assert.assertNotSame(first, second);
        Assert.assertEquals(first, second);
        pool.onConnect(first);
        pool.onConnect(second);
        assertCounts(1, 1);
        assertPeers(first, second);

        pool.onDisconnect(closeFirst ? first : second);
        Channel survivor = closeFirst ? second : first;
        assertCounts(survivor.isActive() ? 1 : 0, survivor.isActive() ? 0 : 1);
        assertPeers(survivor);
        pool.onDisconnect(survivor);
        assertCounts(0, 0);
        assertPeers();
      }
    }
  }

  @Test
  public void unregisteredSameAddressDisconnectDoesNotRemoveRegisteredPeer() throws Exception {
    for (boolean active : new boolean[]{false, true}) {
      Channel registered = channel(active);
      Channel unregistered = channel(!active);
      pool.onConnect(registered);
      Assert.assertFalse(unregistered.isFinishHandshake());

      pool.onDisconnect(unregistered);
      pool.onDisconnect(unregistered);
      assertCounts(active ? 1 : 0, active ? 0 : 1);
      assertPeers(registered);
      pool.onDisconnect(registered);
      assertCounts(0, 0);
      assertPeers();
    }
  }

  @Test
  public void duplicateCallbacksCountEachInstanceOnce() throws Exception {
    Channel inbound = channel(false);
    Channel outbound = channel(true);
    pool.onConnect(inbound);
    pool.onConnect(inbound);
    pool.onConnect(outbound);
    pool.onConnect(outbound);
    assertCounts(1, 1);
    assertPeers(inbound, outbound);

    pool.onDisconnect(inbound);
    pool.onDisconnect(inbound);
    assertCounts(1, 0);
    assertPeers(outbound);
    pool.onDisconnect(outbound);
    pool.onDisconnect(outbound);
    assertCounts(0, 0);
    assertPeers();
  }

  @Test
  public void lateDisconnectDoesNotRemoveReplacementAtSameAddress() throws Exception {
    for (boolean active : new boolean[]{false, true}) {
      Channel old = channel(active);
      pool.onConnect(old);
      pool.onDisconnect(old);
      Channel replacement = channel(!active);
      pool.onConnect(replacement);

      pool.onDisconnect(old);
      assertCounts(active ? 0 : 1, active ? 1 : 0);
      assertPeers(replacement);
      pool.onDisconnect(replacement);
      assertCounts(0, 0);
      assertPeers();
    }
  }

  @Test
  public void repeatedSameAddressLifecyclesDoNotAccumulateCounterDrift() throws Exception {
    for (int i = 0; i < 100; i++) {
      Channel first = channel(i % 2 == 0);
      Channel second = channel(!first.isActive());
      pool.onConnect(first);
      pool.onConnect(second);
      pool.onDisconnect(second);
      pool.onDisconnect(first);
      assertCounts(0, 0);
      assertPeers();
    }
  }

  /**
   * A remote host supporting TCP port reuse can bind an outgoing socket to its listening port.
   * Inbound and outbound connections can then share the remote address seen by this node.
   * If NAT is involved, its port mappings must also preserve that address equality.
   * Model these channels directly without requiring platform-specific socket options.
   */
  private Channel channel(boolean active) throws Exception {
    Channel channel = new Channel();
    set(channel, "inetSocketAddress", REMOTE);
    set(channel, "inetAddress", REMOTE.getAddress());
    set(channel, "isActive", active);
    channels.add(channel);
    return channel;
  }

  private void assertCounts(int active, int passive) {
    Assert.assertEquals(active, pool.getActivePeersCount().get());
    Assert.assertEquals(passive, pool.getPassivePeersCount().get());
  }

  @SuppressWarnings("unchecked")
  private void assertPeers(Channel... expected) throws Exception {
    Field field = ConnPoolService.class.getDeclaredField("activePeers");
    field.setAccessible(true);
    List<Channel> actual = (List<Channel>) field.get(pool);
    Assert.assertEquals(expected.length, actual.size());
    for (int i = 0; i < expected.length; i++) {
      Assert.assertSame(expected[i], actual.get(i));
    }
  }

  private void set(Channel channel, String name, Object value) throws Exception {
    Field field = Channel.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(channel, value);
  }

  private void clearChannels() {
    ChannelManager.getAllChannels().forEach(ChannelManager::notifyDisconnect);
    ChannelManager.getBannedNodes().invalidateAll();
  }
}
