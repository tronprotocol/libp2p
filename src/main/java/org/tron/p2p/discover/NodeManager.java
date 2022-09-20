package org.tron.p2p.discover;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.tron.p2p.discover.protocol.kad.KadService;

public class NodeManager {

  @Getter
  private Node homeNode;

  private Map<String, NodeHandler> nodeHandlerMap = new ConcurrentHashMap<>();

  private DiscoverService discoverService;

  public void init() {
    discoverService = new KadService();
    discoverService.init();
  }

  public void close() {
    discoverService.close();
  }

  public Node updateNode(Node node) {
    return node;
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
    return homeNode;
  }

}
