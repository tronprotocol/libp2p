package org.tron.p2p.discover.protocol.kad;

import org.checkerframework.checker.units.qual.N;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.message.kad.FindNodeMessage;
import org.tron.p2p.discover.message.kad.NeighborsMessage;
import org.tron.p2p.discover.message.kad.PingMessage;
import org.tron.p2p.discover.message.kad.PongMessage;
import org.tron.p2p.discover.socket.UdpEvent;
import org.tron.p2p.utils.NetUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;

public class NodeHandlerTest {

  private static KadService kadService;
  private static Node currNode;
  private static Node oldNode;
  private static Node replaceNode;
  private static NodeHandler currHandler;
  private static NodeHandler oldHandler;
  private static NodeHandler replaceHandler;

  @BeforeClass
  public static void init() {
    Parameter.p2pConfig = new P2pConfig();
    Parameter.p2pConfig.setDiscoverEnable(false);
    kadService = new KadService();
    kadService.init();
    KadService.setPingTimeout(300);
    currNode = new Node(new InetSocketAddress("127.0.0.1", 22222));
    oldNode = new Node(new InetSocketAddress("127.0.0.2", 22222));
    replaceNode = new Node(new InetSocketAddress("127.0.0.3", 22222));
    currHandler = new NodeHandler(currNode, kadService);
    oldHandler = new NodeHandler(oldNode, kadService);
    replaceHandler = new NodeHandler(replaceNode, kadService);
  }

  @Test
  public void test() throws InterruptedException {
    Assert.assertEquals(NodeHandler.State.DISCOVERED, currHandler.getState());
    Assert.assertEquals(NodeHandler.State.DISCOVERED, oldHandler.getState());
    Assert.assertEquals(NodeHandler.State.DISCOVERED, replaceHandler.getState());
    Thread.sleep(2000);
    Assert.assertEquals(NodeHandler.State.DEAD, currHandler.getState());
    Assert.assertEquals(NodeHandler.State.DEAD, oldHandler.getState());
    Assert.assertEquals(NodeHandler.State.DEAD, replaceHandler.getState());

    PingMessage msg = new PingMessage(currNode, kadService.getPublicHomeNode());
    currHandler.handlePing(msg);
    Assert.assertEquals(NodeHandler.State.DISCOVERED, currHandler.getState());
    PongMessage msg1 = new PongMessage(currNode);
    currHandler.handlePong(msg1);
    Assert.assertEquals(NodeHandler.State.ACTIVE, currHandler.getState());
    Assert.assertTrue(kadService.getTable().contains(currNode));
    kadService.getTable().dropNode(currNode);
  }

  @Test
  public void testChangeState() throws Exception {
    currHandler.changeState(NodeHandler.State.ALIVE);
    Assert.assertEquals(NodeHandler.State.ACTIVE, currHandler.getState());
    Assert.assertTrue(kadService.getTable().contains(currNode));

    Class<NodeHandler> clazz = NodeHandler.class;
    Constructor<NodeHandler> cn = clazz.getDeclaredConstructor(Node.class, KadService.class);
    NodeHandler nh = cn.newInstance(oldNode, kadService);
    Field declaredField = clazz.getDeclaredField("replaceCandidate");
    declaredField.setAccessible(true);
    declaredField.set(nh, replaceHandler);

    kadService.getTable().addNode(oldNode);
    nh.changeState(NodeHandler.State.EVICTCANDIDATE);
    nh.changeState(NodeHandler.State.DEAD);
    replaceHandler.changeState(NodeHandler.State.ALIVE);

    Assert.assertFalse(kadService.getTable().contains(oldNode));
    Assert.assertTrue(kadService.getTable().contains(replaceNode));
  }

  @Test
  public void testSendFindNode() throws Exception {
    byte[] nodeId = NetUtil.getNodeId();
    Node node = new Node(nodeId, "127.0.0.1", "", 1);
    NodeHandler handler = new NodeHandler(node, kadService);

    kadService.getTable().addNode(node);

    for (int i = 0; i < 2; i++) {
      String ip = "127.0.1." + i;
      kadService.getTable().addNode(new Node(nodeId, ip, "", 1));
    }

    for (int i = 0; i < 6; i++) {
      handler.sendFindNode(NetUtil.getNodeId());
    }

    Assert.assertFalse(handler.getState().equals(NodeHandler.State.DEAD));

    kadService.getTable().addNode(new Node(nodeId, "127.0.1.4", "", 1));

    handler.sendFindNode(NetUtil.getNodeId());

    Assert.assertTrue(handler.getState().equals(NodeHandler.State.DEAD));
  }

  @Test
  public void testFindNodeRequiresPong() {
    List<UdpEvent> sent = new ArrayList<>();
    KadService service = new KadService() {
      @Override
      public void sendOutbound(UdpEvent event) {
        sent.add(event);
      }
    };
    service.init();
    service.getPongTimer().shutdownNow();
    try {
      Node requester = new Node(NetUtil.getNodeId(), "127.0.0.4", "", 22222);
      InetSocketAddress sender = requester.getInetSocketAddressV4();
      FindNodeMessage findNode = new FindNodeMessage(requester, NetUtil.getNodeId());
      UdpEvent findEvent = new UdpEvent(findNode, sender);
      PingMessage ping = new PingMessage(requester, service.getPublicHomeNode());
      service.getTable().addNode(oldNode);

      service.handleEvent(findEvent);
      Assert.assertEquals(1, sent.size());
      Assert.assertTrue(sent.get(0).getMessage() instanceof PingMessage);
      NodeHandler handler = service.getNodeHandler(requester);
      Assert.assertEquals(NodeHandler.State.DISCOVERED, handler.getState());

      sent.clear();
      service.handleEvent(findEvent);
      Assert.assertTrue(sent.isEmpty());
      service.handleEvent(new UdpEvent(ping, sender));
      Assert.assertEquals(1, sent.size());
      Assert.assertTrue(sent.get(0).getMessage() instanceof PongMessage);
      sent.clear();
      service.handleEvent(findEvent);
      Assert.assertTrue(sent.isEmpty());

      // Exhaust retries without a Pong; a dead endpoint must not receive neighbors either.
      for (int i = 0; i < 4; i++) {
        handler.handleTimedOut();
      }
      Assert.assertEquals(NodeHandler.State.DEAD, handler.getState());
      sent.clear();
      service.handleEvent(findEvent);
      Assert.assertTrue(sent.isEmpty());

      service.handleEvent(new UdpEvent(ping, sender));
      service.handleEvent(new UdpEvent(new PongMessage(requester), sender));
      Assert.assertEquals(NodeHandler.State.ACTIVE, handler.getState());
      sent.clear();
      service.handleEvent(findEvent);
      Assert.assertEquals(1, sent.size());
      Assert.assertTrue(sent.get(0).getMessage() instanceof NeighborsMessage);
      NeighborsMessage neighbors = (NeighborsMessage) sent.get(0).getMessage();
      Assert.assertTrue(neighbors.getNodes().contains(oldNode));
      Assert.assertEquals(findNode.getTimestamp(), neighbors.getTimestamp());
      Assert.assertEquals(sender, sent.get(0).getAddress());

      // An established node remains eligible while its eviction check is pending.
      handler.changeState(NodeHandler.State.EVICTCANDIDATE);
      sent.clear();
      service.handleEvent(findEvent);
      Assert.assertEquals(1, sent.size());
      Assert.assertTrue(sent.get(0).getMessage() instanceof NeighborsMessage);
    } finally {
      service.close();
    }
  }

  @AfterClass
  public static void destroy() {
    kadService.close();
  }
}
