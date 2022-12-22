package org.tron.p2p.dns;


import java.util.List;
import org.tron.p2p.discover.Node;
import org.tron.p2p.dns.update.PublishService;

public class DnsManager {

  private static PublishService publishService;

  public static void init() {
    publishService = new PublishService();
    publishService.init();
  }

  public static void close() {
    if (publishService != null) {
      publishService.close();
    }
  }

  public List<Node> getAllNodes() {
    return null;
  }

  public Node getRandomNodes() {
    return null;
  }
}
