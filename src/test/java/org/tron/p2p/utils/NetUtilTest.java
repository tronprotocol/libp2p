package org.tron.p2p.utils;

import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Discover;

import java.net.InetSocketAddress;

public class NetUtilTest {

  @Test
  public void testValidIp() {
    boolean flag = NetUtil.validIp(null);
    Assert.assertTrue(!flag);
    flag = NetUtil.validIp("a.1.1.1");
    Assert.assertTrue(!flag);
    flag = NetUtil.validIp("1.1.1");
    Assert.assertTrue(!flag);
    flag = NetUtil.validIp("0.0.0.0");
    Assert.assertTrue(!flag);
    flag = NetUtil.validIp("256.1.2.3");
    Assert.assertTrue(!flag);
    flag = NetUtil.validIp("1.1.1.1");
    Assert.assertTrue(flag);
  }

  @Test
  public void testValidNode() {
    boolean flag = NetUtil.validNode(null);
    Assert.assertTrue(!flag);

    InetSocketAddress address = new InetSocketAddress("1.1.1.1", 1000);
    Node node = new Node(address);
    flag = NetUtil.validNode(node);
    Assert.assertTrue(flag);

    node.setId(new byte[10]);
    flag = NetUtil.validNode(node);
    Assert.assertTrue(!flag);

    node = new Node(NetUtil.getNodeId(), "1.1.1", 1000);
    flag = NetUtil.validNode(node);
    Assert.assertTrue(!flag);
  }

  @Test
  public void testGetNode() {
    Discover.Endpoint endpoint = Discover.Endpoint.newBuilder()
            .setPort(100).build();
    Node node = NetUtil.getNode(endpoint);
    Assert.assertEquals(100, node.getPort());
  }

}
