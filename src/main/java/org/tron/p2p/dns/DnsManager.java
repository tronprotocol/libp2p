package org.tron.p2p.dns;


import java.util.ArrayList;
import java.util.List;
import org.tron.p2p.discover.Node;
import org.tron.p2p.dns.sync.Client;
import org.tron.p2p.dns.sync.RandomIterator;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.dns.update.PublishService;

public class DnsManager {

  private static PublishService publishService;
  private static Client syncClient;
  private static RandomIterator randomIterator;

  public static void init() {
    publishService = new PublishService();
    syncClient = new Client();
    publishService.init();
    syncClient.init();
    randomIterator = syncClient.newIterator();
  }

  public static void close() {
    if (publishService != null) {
      publishService.close();
    }
    if (syncClient != null) {
      syncClient.close();
    }
    if (randomIterator != null) {
      randomIterator.close();
    }
  }

  public static List<Node> getAllNodes() {
    List<Node> nodes = new ArrayList<>();
    for (Tree tree : syncClient.getTrees().values()) {
      nodes.addAll(tree.getNodes());
    }
    return nodes;
  }

  public static Node getRandomNodes() {
    return randomIterator.next();
  }
}
