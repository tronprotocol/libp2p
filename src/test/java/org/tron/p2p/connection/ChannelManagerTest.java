package org.tron.p2p.connection;

import java.net.InetSocketAddress;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;

public class ChannelManagerTest {

  private static String localIp = "127.0.0.1";
  private static int port = 10000;

  @Before
  public void init() {
    Parameter.p2pConfig = new P2pConfig();
    Parameter.p2pConfig.setDiscoverEnable(false);

    NodeManager.init();
    ChannelManager.init();
  }

  private Channel getChannel(InetSocketAddress inetSocketAddress) {
    Node node = new Node(inetSocketAddress);
    Channel channel = new Channel();
    channel.setInetSocketAddress(inetSocketAddress);
    channel.setInetAddress(inetSocketAddress.getAddress());
    channel.setNodeId(node.getHexId());

    return channel;
  }

  private void clearChannels() {
    ChannelManager.getChannels().clear();
    ChannelManager.getBannedNodes().cleanUp();
  }

  private void addChannels() {
    clearChannels();
    String[] ips = new String[] {"127.0.0.1", "127.0.0.2", "127.0.0.3", "127.0.0.4"};
    int[] ports = new int[] {port, port, port, port};

    for (int i = 0; i < ips.length; i++) {
      InetSocketAddress inetSocketAddress = new InetSocketAddress(ips[i], ports[i]);
      Channel channel = getChannel(inetSocketAddress);
      ChannelManager.getChannels().put(inetSocketAddress, channel);
    }
  }

  @Test
  public void testBannedNode() {
    clearChannels();
    InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1", port);
    long banTime = 1_000L;
    ChannelManager.banNode(inetSocketAddress.getAddress(), banTime);
    Channel channel = getChannel(inetSocketAddress);
    DisconnectCode disconnectCode = ChannelManager.processPeer(channel);
    Assert.assertEquals(DisconnectCode.TIME_BANNED, disconnectCode);

    try {
      Thread.sleep(banTime);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    disconnectCode = ChannelManager.processPeer(channel);
    Assert.assertNotEquals(DisconnectCode.TIME_BANNED, disconnectCode);
  }

  @Test
  public void testTooManyPeers() {
    addChannels();
    Parameter.p2pConfig.setMaxConnections(4);
    Assert.assertEquals(4, ChannelManager.getChannels().size());

    InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.5", port);
    Channel channel = getChannel(inetSocketAddress);
    DisconnectCode disconnectCode = ChannelManager.processPeer(channel);
    Assert.assertEquals(DisconnectCode.TOO_MANY_PEERS, disconnectCode);
  }

  @Test
  public void testGetConnectionNum() {
    addChannels();

    InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1", port + 1);
    Assert.assertEquals(1, ChannelManager.getConnectionNum(inetSocketAddress.getAddress()));

    Channel channel = getChannel(inetSocketAddress);
    DisconnectCode disconnectCode = ChannelManager.processPeer(channel);
    Assert.assertEquals(DisconnectCode.NORMAL, disconnectCode);

    Assert.assertEquals(2, ChannelManager.getConnectionNum(inetSocketAddress.getAddress()));
  }

  @Test
  public void testTooManyPeersWithSameIp() {
    clearChannels();
    InetSocketAddress inetSocketAddress1 = new InetSocketAddress("127.0.0.1", port);
    Channel channel1 = getChannel(inetSocketAddress1);
    DisconnectCode disconnectCode = ChannelManager.processPeer(channel1);
    Assert.assertEquals(DisconnectCode.NORMAL, disconnectCode);

    InetSocketAddress inetSocketAddress2 = new InetSocketAddress("127.0.0.1", port + 1);
    Channel channel2 = getChannel(inetSocketAddress2);
    DisconnectCode disconnectCode2 = ChannelManager.processPeer(channel2);
    Assert.assertEquals(DisconnectCode.NORMAL, disconnectCode2);

    InetSocketAddress inetSocketAddress3 = new InetSocketAddress("127.0.0.1", port + 2);
    Channel channel3 = getChannel(inetSocketAddress3);
    DisconnectCode disconnectCode3 = ChannelManager.processPeer(channel3);
    Assert.assertEquals(DisconnectCode.MAX_CONNECTION_WITH_SAME_IP, disconnectCode3);
  }

  @Test
  public void testSameChannelWithDifferentId() {
    clearChannels();
    InetSocketAddress inetSocketAddress1 = new InetSocketAddress("127.0.0.1", port);
    Channel channel1 = getChannel(inetSocketAddress1);
    DisconnectCode disconnectCode = ChannelManager.processPeer(channel1);
    Assert.assertEquals(DisconnectCode.NORMAL, disconnectCode);

    InetSocketAddress inetSocketAddress2 = new InetSocketAddress("127.0.0.1", port);
    Channel channel2 = getChannel(inetSocketAddress2);
    Assert.assertNotEquals(channel1.getNodeId(), channel2.getNodeId());

    DisconnectCode disconnectCode2 = ChannelManager.processPeer(channel2);
    Assert.assertEquals(DisconnectCode.NORMAL, disconnectCode2);
  }

  @Test
  public void testDuplicatePeer() {
    clearChannels();
    InetSocketAddress inetSocketAddress1 = new InetSocketAddress("127.0.0.1", port);
    Channel channel1 = getChannel(inetSocketAddress1);
    DisconnectCode disconnectCode = ChannelManager.processPeer(channel1);
    Assert.assertEquals(DisconnectCode.NORMAL, disconnectCode);

    DisconnectCode disconnectCode2 = ChannelManager.processPeer(channel1);
    Assert.assertEquals(DisconnectCode.DUPLICATE_PEER, disconnectCode2);
  }

  @Test
  public void testSameChannelWithDifferentStartTime() {
    clearChannels();
    InetSocketAddress inetSocketAddress = new InetSocketAddress("127.0.0.1", port);

    Node node = new Node(inetSocketAddress);
    Channel channel1 = new Channel();
    channel1.setInetSocketAddress(inetSocketAddress);
    channel1.setInetAddress(inetSocketAddress.getAddress());
    channel1.setNodeId(node.getHexId());

    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    Channel channel2 = new Channel();
    channel2.setInetSocketAddress(inetSocketAddress);
    channel2.setInetAddress(inetSocketAddress.getAddress());
    channel2.setNodeId(node.getHexId());

    Assert.assertTrue(channel1.getStartTime() < channel2.getStartTime());

    DisconnectCode disconnectCode = ChannelManager.processPeer(channel1);
    Assert.assertEquals(DisconnectCode.NORMAL, disconnectCode);

    //drop latest
    DisconnectCode disconnectCode2 = ChannelManager.processPeer(channel2);
    Assert.assertEquals(DisconnectCode.DUPLICATE_PEER, disconnectCode2);
  }

  @After
  public void destroy() {
    NodeManager.close();
    ChannelManager.close();
  }
}
