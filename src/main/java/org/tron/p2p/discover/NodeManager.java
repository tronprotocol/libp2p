package org.tron.p2p.discover;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.discover.protocol.kad.KadService;
import org.tron.p2p.discover.socket.DiscoverServer;

@Slf4j(topic = "net")
public class NodeManager {

  private DiscoverService discoverService;
  private DiscoverServer discoverServer;

  public void init() {
    discoverService = new KadService();
    discoverService.init();
    discoverServer = new DiscoverServer();
    new Thread(() -> {
      try {
        discoverServer.init(discoverService);
      } catch (Exception e) {
        log.error("Discovery server start failed", e);
      }
    }, "DiscoverServer").start();
  }

  public void close() {
    discoverService.close();
    discoverServer.close();
  }

  public Node updateNode(Node node) {
    return discoverService.updateNode(node);
  }

  public List<Node> getConnectableNodes() {
    return discoverService.getConnectableNodes();
  }

  public List<Node> getTableNodes() {
    return discoverService.getTableNodes();
  }

  public List<Node> getAllNodes() {
    return discoverService.getAllNodes();
  }

  public Node getPublicHomeNode() {
    return discoverService.getPublicHomeNode();
  }

}
