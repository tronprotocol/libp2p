package org.tron.p2p.discover.protocol.kad;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.message.Message;
import org.tron.p2p.discover.message.kad.FindNodeMessage;
import org.tron.p2p.discover.message.kad.NeighborsMessage;
import org.tron.p2p.discover.message.kad.PingMessage;
import org.tron.p2p.discover.message.kad.PongMessage;
import org.tron.p2p.discover.socket.UdpEvent;
import org.tron.p2p.protos.Discover;

public class FindNodePongVerificationTest {
  private static final int NETWORK_ID = 11111;
  private final List<UdpEvent> sent = new ArrayList<>();
  private P2pConfig previousConfig;
  private KadService service;
  private Node requester;
  private Node neighbor;
  private InetSocketAddress sender;

  @Before
  public void setUp() {
    previousConfig = Parameter.p2pConfig;
    Parameter.p2pConfig = new P2pConfig();
    Parameter.p2pConfig.setNodeID(new byte[64]);
    Parameter.p2pConfig.setIp("127.0.0.1");
    Parameter.p2pConfig.setIpv6("");
    Parameter.p2pConfig.setNetworkId(NETWORK_ID);
    Parameter.p2pConfig.setDiscoverEnable(false);
    service = new KadService() {
      @Override
      public void sendOutbound(UdpEvent event) {
        sent.add(event);
      }
    };
    service.init();
    // Exercise the timeout transitions explicitly, without racing scheduled tasks.
    service.getPongTimer().shutdownNow();
    requester = node(2);
    neighbor = node(3);
    sender = new InetSocketAddress(requester.getHostV4(), 40000);
    service.getTable().addNode(neighbor);
  }

  @After
  public void tearDown() {
    service.close();
    Parameter.p2pConfig = previousConfig;
  }

  @Test
  public void matchingPongAllowsMappedPortWithoutMakingNodeConnectible() throws Exception {
    find();
    Assert.assertEquals(1, sent.size());
    Assert.assertTrue(sent.get(0).getMessage() instanceof PingMessage);
    Assert.assertEquals(sender, sent.get(0).getAddress());
    receive(new PongMessage(requester));

    Assert.assertEquals(NodeHandler.State.DEAD, handler().getState());
    Assert.assertFalse(handler().getNode().isConnectible(NETWORK_ID));
    Assert.assertFalse(service.getTable().contains(handler().getNode()));
    Assert.assertFalse(service.getConnectableNodes().contains(handler().getNode()));
    assertNeighbors();

    // A subsequent same-network Ping must not revoke verification solely due to the port mismatch.
    receive(new PingMessage(requester, service.getPublicHomeNode()));
    Assert.assertEquals(NodeHandler.State.DEAD, handler().getState());
    assertNeighbors();
  }

  @Test
  public void pingAloneDoesNotAuthorizeNeighbors() throws Exception {
    find();
    receive(new PingMessage(requester, service.getPublicHomeNode()));
    assertNoNeighbors();
  }

  @Test
  public void wrongNetworkPongDoesNotAuthorizeNeighbors() throws Exception {
    find();
    receive(pong(NETWORK_ID + 1));
    assertNoNeighbors();
    // The invalid response consumed the pending Ping; an unsolicited valid Pong cannot verify it.
    receive(new PongMessage(requester));
    assertNoNeighbors();
  }

  @Test
  public void wrongNetworkPingRevokesPreviousVerification() throws Exception {
    verify();
    PingMessage ping = new PingMessage(requester, service.getPublicHomeNode());
    receive(new PingMessage(Discover.PingMessage.parseFrom(ping.getData()).toBuilder()
        .setVersion(NETWORK_ID + 1).build().toByteArray()));
    assertNoNeighbors();
  }

  @Test
  public void wrongNetworkPongRevokesPreviousVerification() throws Exception {
    verify();
    handler().sendPing();
    receive(pong(NETWORK_ID + 1));
    assertNoNeighbors();
  }

  @Test
  public void exhaustedPingRetriesRevokeVerificationAndRejectLatePong() throws Exception {
    verify();
    handler().sendPing();
    for (int i = 0; i < 3; i++) {
      handler().handleTimedOut();
      assertNeighbors();
    }
    handler().handleTimedOut();
    assertNoNeighbors();
    receive(new PongMessage(requester));
    assertNoNeighbors();
  }

  @Test
  public void routingStateAloneDoesNotAuthorizeNeighbors() throws Exception {
    sender = requester.getInetSocketAddressV4();
    find();
    handler().changeState(NodeHandler.State.ALIVE);
    Assert.assertEquals(NodeHandler.State.ACTIVE, handler().getState());
    assertNoNeighbors();
  }

  @Test
  public void failedFindNodeEvictionRevokesVerification() throws Exception {
    sender = requester.getInetSocketAddressV4();
    verify();
    // All four nodes share a bucket, meeting the existing failed-FindNode eviction threshold.
    service.getTable().addNode(node(4));
    service.getTable().addNode(node(5));
    Assert.assertEquals(4, service.getTable().getNodesCount());
    for (int i = 0; i < 6; i++) {
      handler().sendFindNode(new byte[64]);
    }
    Assert.assertEquals(NodeHandler.State.DEAD, handler().getState());
    Assert.assertFalse(service.getTable().contains(handler().getNode()));
    assertNoNeighbors();
  }

  private void verify() throws Exception {
    find();
    receive(new PongMessage(requester));
    assertNeighbors();
  }

  private void assertNeighbors() throws Exception {
    sent.clear();
    FindNodeMessage request = find();
    Assert.assertEquals(1, sent.size());
    Assert.assertTrue(sent.get(0).getMessage() instanceof NeighborsMessage);
    NeighborsMessage response = (NeighborsMessage) sent.get(0).getMessage();
    Assert.assertTrue(response.getNodes().contains(neighbor));
    Assert.assertEquals(request.getTimestamp(), response.getTimestamp());
    Assert.assertEquals(sender, sent.get(0).getAddress());
  }

  private void assertNoNeighbors() throws Exception {
    sent.clear();
    find();
    Assert.assertTrue(sent.isEmpty());
  }

  private FindNodeMessage find() throws Exception {
    FindNodeMessage request = new FindNodeMessage(requester, new byte[64]);
    receive(request);
    return request;
  }

  private void receive(Message message) throws Exception {
    service.handleEvent(new UdpEvent(Message.parse(message.getSendData()), sender));
  }

  private NodeHandler handler() {
    return service.getNodeHandler(new Node(requester.getId(), sender.getHostString(), "",
        sender.getPort(), requester.getPort()));
  }

  private PongMessage pong(int networkId) throws Exception {
    PongMessage pong = new PongMessage(requester);
    return new PongMessage(Discover.PongMessage.parseFrom(pong.getData()).toBuilder()
        .setEcho(networkId).build().toByteArray());
  }

  private Node node(int suffix) {
    byte[] id = new byte[64];
    id[0] = (byte) 0x80;
    id[63] = (byte) suffix;
    return new Node(id, "127.0.0." + suffix, "", 18888);
  }
}
