package org.tron.p2p.connection;


import io.netty.channel.ChannelFuture;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.pool.ConnPoolService;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.DiscoverService;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.discover.protocol.kad.KadService;

public class ConnPoolServiceTest {

  private static String localIp = "127.0.0.1";
  private static int port = 10000;

  private static class CountingPeerClient extends PeerClient {

    private final AtomicInteger connectCount = new AtomicInteger();

    @Override
    public ChannelFuture connectAsync(Node node, boolean discoveryMode) {
      connectCount.incrementAndGet();
      return null;
    }
  }

  @BeforeClass
  public static void init() {
    Parameter.p2pConfig = new P2pConfig();
    Parameter.p2pConfig.setDiscoverEnable(false);
    Parameter.p2pConfig.setPort(port);

    NodeManager.init();
    ChannelManager.init();
  }

  private void clearChannels() {
    ChannelManager.getChannels().clear();
    ChannelManager.getBannedNodes().invalidateAll();
  }

  private static Method setPeerClientAndGetConnectMethod(ConnPoolService connPoolService,
      PeerClient peerClient) throws Exception {
    Field peerClientField = ConnPoolService.class.getDeclaredField("peerClient");
    peerClientField.setAccessible(true);
    peerClientField.set(connPoolService, peerClient);
    Method connect = ConnPoolService.class.getDeclaredMethod("connect", boolean.class);
    connect.setAccessible(true);
    return connect;
  }

  @Test
  public void connectDoesNotRetryCachedActiveNode() throws Exception {
    InetSocketAddress activeNode = new InetSocketAddress("127.0.0.2", 18888);
    Parameter.p2pConfig.setActiveNodes(Collections.singletonList(activeNode));
    Parameter.p2pConfig.setMinConnections(0);
    Parameter.p2pConfig.setMinActiveConnections(0);
    try {
      ConnPoolService connPoolService = new ConnPoolService();
      CountingPeerClient peerClient = new CountingPeerClient();
      Method connect = setPeerClientAndGetConnectMethod(connPoolService, peerClient);
      connect.invoke(connPoolService, false);
      connect.invoke(connPoolService, false);

      Assert.assertEquals(1, peerClient.connectCount.get());
    } finally {
      Parameter.p2pConfig.setActiveNodes(Collections.emptyList());
      Parameter.p2pConfig.setMinConnections(8);
      Parameter.p2pConfig.setMinActiveConnections(3);
    }
  }

  @Test
  public void triggerConnectClearsCachedActiveNode() throws Exception {
    InetSocketAddress activeNode = new InetSocketAddress("127.0.0.3", 18888);
    Parameter.p2pConfig.setActiveNodes(Collections.singletonList(activeNode));
    Parameter.p2pConfig.setMinConnections(0);
    Parameter.p2pConfig.setMinActiveConnections(0);
    try {
      ConnPoolService connPoolService = new ConnPoolService();
      CountingPeerClient peerClient = new CountingPeerClient();
      Method connect = setPeerClientAndGetConnectMethod(connPoolService, peerClient);
      connect.invoke(connPoolService, false);
      connPoolService.triggerConnect(activeNode);
      connect.invoke(connPoolService, false);

      Assert.assertEquals(2, peerClient.connectCount.get());
    } finally {
      Parameter.p2pConfig.setActiveNodes(Collections.emptyList());
      Parameter.p2pConfig.setMinConnections(8);
      Parameter.p2pConfig.setMinActiveConnections(3);
    }
  }

  @Test
  public void fastConnectionFailureAllowsRetryOnNextTick() throws Exception {
    InetSocketAddress activeNode = new InetSocketAddress("127.0.0.4", 18888);
    Parameter.p2pConfig.setActiveNodes(Collections.singletonList(activeNode));
    Parameter.p2pConfig.setMinConnections(0);
    Parameter.p2pConfig.setMinActiveConnections(0);
    try {
      ConnPoolService connPoolService = new ConnPoolService();
      AtomicInteger attempts = new AtomicInteger();
      PeerClient peerClient = new PeerClient() {
        @Override
        public ChannelFuture connectAsync(Node node, boolean discoveryMode) {
          attempts.incrementAndGet();
          connPoolService.triggerConnect(node.getPreferInetSocketAddress());
          return null;
        }
      };
      Method connect = setPeerClientAndGetConnectMethod(connPoolService, peerClient);
      connect.invoke(connPoolService, false);
      connect.invoke(connPoolService, false);

      Assert.assertEquals(2, attempts.get());
    } finally {
      Parameter.p2pConfig.setActiveNodes(Collections.emptyList());
      Parameter.p2pConfig.setMinConnections(8);
      Parameter.p2pConfig.setMinActiveConnections(3);
    }
  }

  @Test
  public void runtimeAddedActiveNodeRetriesAfterFailure() throws Exception {
    List<InetSocketAddress> activeNodes = new ArrayList<>();
    Parameter.p2pConfig.setActiveNodes(activeNodes);
    Parameter.p2pConfig.setMinConnections(0);
    Parameter.p2pConfig.setMinActiveConnections(0);
    try {
      ConnPoolService connPoolService = new ConnPoolService();
      InetSocketAddress activeNode = new InetSocketAddress("127.0.0.5", 18888);
      activeNodes.add(activeNode);
      AtomicInteger attempts = new AtomicInteger();
      PeerClient peerClient = new PeerClient() {
        @Override
        public ChannelFuture connectAsync(Node node, boolean discoveryMode) {
          attempts.incrementAndGet();
          connPoolService.triggerConnect(node.getPreferInetSocketAddress());
          return null;
        }
      };
      Method connect = setPeerClientAndGetConnectMethod(connPoolService, peerClient);
      connect.invoke(connPoolService, false);
      connect.invoke(connPoolService, false);

      Assert.assertEquals(2, attempts.get());
    } finally {
      Parameter.p2pConfig.setActiveNodes(Collections.emptyList());
      Parameter.p2pConfig.setMinConnections(8);
      Parameter.p2pConfig.setMinActiveConnections(3);
    }
  }

  @Test
  public void runtimeAddedActiveNodeFailureReplenishesWithoutRedialingIt()
      throws Exception {
    List<InetSocketAddress> activeNodes = new ArrayList<>();
    Parameter.p2pConfig.setActiveNodes(activeNodes);
    Parameter.p2pConfig.setMinConnections(1);
    Parameter.p2pConfig.setMinActiveConnections(0);
    Field discoveryField = NodeManager.class.getDeclaredField("discoverService");
    discoveryField.setAccessible(true);
    DiscoverService previousDiscovery = (DiscoverService) discoveryField.get(null);
    ConnPoolService connPoolService = null;
    try {
      connPoolService = new ConnPoolService();
      InetSocketAddress activeNode = new InetSocketAddress("127.0.0.6", 18888);
      InetSocketAddress otherNode = new InetSocketAddress("127.0.0.7", 18888);
      activeNodes.add(activeNode);
      Node candidate = new Node(new byte[64], "127.0.0.6", "", 18888);
      Node otherCandidate = new Node(new byte[64], "127.0.0.7", "", 18888);
      discoveryField.set(null, new KadService() {
        @Override
        public List<Node> getConnectableNodes() {
          List<Node> nodes = new ArrayList<>();
          nodes.add(candidate);
          nodes.add(otherCandidate);
          return nodes;
        }
      });
      AtomicInteger attempts = new AtomicInteger();
      List<InetSocketAddress> dialed = Collections.synchronizedList(new ArrayList<>());
      ConnPoolService pool = connPoolService;
      PeerClient peerClient = new PeerClient() {
        @Override
        public ChannelFuture connectAsync(Node node, boolean discoveryMode) {
          dialed.add(node.getPreferInetSocketAddress());
          if (attempts.incrementAndGet() == 1) {
            pool.triggerConnect(node.getPreferInetSocketAddress());
          }
          return null;
        }
      };
      Method connect = setPeerClientAndGetConnectMethod(pool, peerClient);
      connect.invoke(pool, false);
      Field executorField = ConnPoolService.class.getDeclaredField("poolLoopExecutor");
      executorField.setAccessible(true);
      ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) executorField.get(pool);
      executor.submit(() -> { }).get(5, TimeUnit.SECONDS);

      Assert.assertEquals(2, attempts.get());
      Assert.assertEquals(activeNode, dialed.get(0));
      Assert.assertEquals(otherNode, dialed.get(1));
      Assert.assertEquals(1, pool.getConnectingPeersCount().get());
    } finally {
      if (connPoolService != null) {
        connPoolService.close();
      }
      discoveryField.set(null, previousDiscovery);
      Parameter.p2pConfig.setActiveNodes(Collections.emptyList());
      Parameter.p2pConfig.setMinConnections(8);
      Parameter.p2pConfig.setMinActiveConnections(3);
    }
  }

  @Test
  public void getNodes_chooseHomeNode() {
    InetSocketAddress localAddress = new InetSocketAddress(Parameter.p2pConfig.getIp(),
        Parameter.p2pConfig.getPort());
    Set<InetSocketAddress> inetInUse = new HashSet<>();
    inetInUse.add(localAddress);

    List<Node> connectableNodes = new ArrayList<>();
    connectableNodes.add(NodeManager.getHomeNode());

    ConnPoolService connPoolService = new ConnPoolService();
    List<Node> nodes = connPoolService.getNodes(new HashSet<>(), inetInUse, connectableNodes,
        1);
    Assert.assertEquals(0, nodes.size());

    nodes = connPoolService.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        1);
    Assert.assertEquals(1, nodes.size());
  }

  @Test
  public void getNodes_respectsLimit() {
    clearChannels();
    Node node1 = new Node(new InetSocketAddress(localIp, 90));
    Node node2 = new Node(new InetSocketAddress(localIp, 100));

    List<Node> connectableNodes = new ArrayList<>();
    connectableNodes.add(node1);
    connectableNodes.add(node2);

    ConnPoolService connPoolService = new ConnPoolService();
    List<Node> nodes = connPoolService.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        2);
    Assert.assertEquals(2, nodes.size());
    // getNodes shuffles candidates, so compare their contents without assuming an order.
    Assert.assertEquals(new HashSet<>(connectableNodes), new HashSet<>(nodes));

    int limit = 1;
    List<Node> nodes2 = connPoolService.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        limit);
    Assert.assertEquals(limit, nodes2.size());
    Assert.assertTrue(connectableNodes.containsAll(nodes2));
  }

  @Test
  public void getNodes_banNode() throws InterruptedException {
    clearChannels();
    InetSocketAddress inetSocketAddress = new InetSocketAddress(localIp, 90);
    long banTime = 500L;
    ChannelManager.banNode(inetSocketAddress.getAddress(), banTime);
    Node node = new Node(inetSocketAddress);
    List<Node> connectableNodes = new ArrayList<>();
    connectableNodes.add(node);

    ConnPoolService connPoolService = new ConnPoolService();
    List<Node> nodes = connPoolService.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        1);
    Assert.assertEquals(0, nodes.size());
    Thread.sleep(2 * banTime);

    nodes = connPoolService.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes, 1);
    Assert.assertEquals(1, nodes.size());
  }

  @Test
  public void getNodes_nodeInUse() {
    clearChannels();
    InetSocketAddress inetSocketAddress = new InetSocketAddress(localIp, 90);
    Node node = new Node(inetSocketAddress);
    List<Node> connectableNodes = new ArrayList<>();
    connectableNodes.add(node);

    Set<String> nodesInUse = new HashSet<>();
    nodesInUse.add(node.getHexId());
    ConnPoolService connPoolService = new ConnPoolService();
    List<Node> nodes = connPoolService.getNodes(nodesInUse, new HashSet<>(), connectableNodes, 1);
    Assert.assertEquals(0, nodes.size());
  }

  @AfterClass
  public static void destroy() {
    NodeManager.close();
    ChannelManager.close();
  }
}
