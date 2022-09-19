package org.tron.p2p.discover;

import org.tron.p2p.discover.protocol.kad.KadService;

import java.util.List;

public class NodeManager {

  private DiscoverService discoverService;

  public void init() {
    discoverService = new KadService();
    discoverService.init();
  }

  public void close() {
    discoverService.close();
  }

  public void updateNode(){

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

}
