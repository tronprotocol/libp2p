package org.tron.p2p.discover;

import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.utils.NetUtil;

import java.net.InetSocketAddress;

public class NodeTest {

  @Test
  public void nodeTest() throws InterruptedException {
    Node node1 = new Node(new InetSocketAddress("127.0.0.1", 10001));
    Assert.assertEquals(64, node1.getId().length);

    Node node2 = new Node(NetUtil.getNodeId(), "127.0.0.1", null, 10002);
    boolean isDif = node1.equals(node2);
    Assert.assertFalse(isDif);

    long lastModifyTime = node1.getUpdateTime();
    Thread.sleep(1);
    node1.touch();
    Assert.assertNotEquals(lastModifyTime, node1.getUpdateTime());

    node1.setP2pVersion(11111);
    Assert.assertTrue(node1.isConnectible(11111));
    Assert.assertFalse(node1.isConnectible(11112));
    Node node3 = new Node(NetUtil.getNodeId(), "127.0.0.1", null, 10003, 10004);
    node3.setP2pVersion(11111);
    Assert.assertFalse(node3.isConnectible(11111));
  }
}
