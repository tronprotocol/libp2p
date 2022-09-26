package org.tron.p2p.discover;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.protocol.kad.KadService;
import org.tron.p2p.discover.socket.DiscoverServer;

@Slf4j(topic = "net")
public class NodeManager {

  private static DiscoverService discoverService;
  private DiscoverServer discoverServer;

  public void init() {
    discoverService = new KadService();
    discoverService.init();
    if (Parameter.p2pConfig.isDiscoverEnable()) {
      discoverServer = new DiscoverServer();
      new Thread(() -> {
        try {
          discoverServer.init(discoverService);
        } catch (Exception e) {
          log.error("Discovery server start failed", e);
        }
      }, "DiscoverServer").start();
    }
  }

  public void close() {
    if (discoverService != null) {
      discoverService.close();
    }
    if (discoverServer != null) {
      discoverServer.close();
    }
  }

  public static Node updateNode(Node node) {
    return discoverService.updateNode(node);
  }

  public static List<Node> getConnectableNodes() {
    return discoverService.getConnectableNodes();
  }

  public static List<Node> getTableNodes() {
    return discoverService.getTableNodes();
  }

  public static List<Node> getAllNodes() {
    return discoverService.getAllNodes();
  }

  public static Node getPublicHomeNode() {
    return discoverService.getPublicHomeNode();
  }

}
