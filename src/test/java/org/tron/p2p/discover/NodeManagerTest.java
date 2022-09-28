package org.tron.p2p.discover;

import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;

import java.net.InetSocketAddress;

public class NodeManagerTest {
  @Test
  public void testNoSeeds() {
    P2pConfig config = new P2pConfig();
    Parameter.p2pConfig = config;
    try {
      NodeManager.init();
      Thread.sleep(100);
      Assert.assertEquals(0, NodeManager.getAllNodes().size());
      Assert.assertEquals(0, NodeManager.getTableNodes().size());
      Assert.assertEquals(0, NodeManager.getConnectableNodes().size());

      Node node = new Node(new InetSocketAddress("127.0.0.1", 18888));
      Node node1 = NodeManager.initNode(node);
      Assert.assertEquals(node, node1);
    } catch (InterruptedException e) {
      e.printStackTrace();
    } finally {
      NodeManager.close();
    }
  }
}
