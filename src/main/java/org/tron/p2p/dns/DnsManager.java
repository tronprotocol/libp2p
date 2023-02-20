package org.tron.p2p.dns;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.discover.Node;
import org.tron.p2p.dns.sync.Client;
import org.tron.p2p.dns.sync.RandomIterator;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.dns.update.PublishService;

@Slf4j(topic = "net")
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

  public static List<DnsNode> getDnsNodes() {
    Set<DnsNode> nodes = new HashSet<>();
    for (Map.Entry<String, Tree> entry : syncClient.getTrees().entrySet()) {
      Tree tree = entry.getValue();
      int v4Size = 0, v6Size = 0;
      List<DnsNode> dnsNodes = tree.getDnsNodes();
      List<DnsNode> ipv6Nodes = new ArrayList<>();
      for (DnsNode dnsNode : dnsNodes) {
        if (dnsNode.getInetSocketAddressV4() != null) {
          v4Size += 1;
        }
        if (dnsNode.getInetSocketAddressV6() != null) {
          v6Size += 1;
          ipv6Nodes.add(dnsNode);
        }
      }
      log.debug("Tree {} node size:{}, v4 node size:{}, v6 node size:{}", entry.getKey(),
          tree.getDnsNodes().size(), v4Size, v6Size);
      if (ipv6Nodes.size() > 0) {
        log.debug("Node with ipv6: {}", ipv6Nodes);
      }
      List<DnsNode> connectAbleNodes = dnsNodes.stream()
          .filter(node -> node.getPreferInetSocketAddress() != null)
          .collect(Collectors.toList());
      nodes.addAll(connectAbleNodes);
    }
    return new ArrayList<>(nodes);
  }

  public static Node getRandomNodes() {
    return randomIterator.next();
  }
}
