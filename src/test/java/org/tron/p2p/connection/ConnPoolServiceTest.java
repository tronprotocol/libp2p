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
import org.tron.p2p.discover.NodeManager;

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
  public void getNodes_orderByUpdateTimeDesc() throws Exception {
    clearChannels();
    Node node1 = new Node(new InetSocketAddress(localIp, 90));
    Field field = node1.getClass().getDeclaredField("updateTime");
    field.setAccessible(true);
    field.set(node1, System.currentTimeMillis());

    Node node2 = new Node(new InetSocketAddress(localIp, 100));
    field = node2.getClass().getDeclaredField("updateTime");
    field.setAccessible(true);
    field.set(node2, System.currentTimeMillis() + 10);

    Assert.assertTrue(node1.getUpdateTime() < node2.getUpdateTime());

    List<Node> connectableNodes = new ArrayList<>();
    connectableNodes.add(node1);
    connectableNodes.add(node2);

    ConnPoolService connPoolService = new ConnPoolService();
    List<Node> nodes = connPoolService.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        2);
    Assert.assertEquals(2, nodes.size());
    Assert.assertTrue(nodes.get(0).getUpdateTime() > nodes.get(1).getUpdateTime());

    int limit = 1;
    List<Node> nodes2 = connPoolService.getNodes(new HashSet<>(), new HashSet<>(), connectableNodes,
        limit);
    Assert.assertEquals(limit, nodes2.size());
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
