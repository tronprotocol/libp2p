package org.tron.p2p.dns.tree;


import java.util.List;
import org.tron.p2p.dns.DnsNode;

public class NodeEntry implements Entry {

  private List<DnsNode> nodes;

  public NodeEntry(List<DnsNode> nodes) {
    this.nodes = nodes;
  }

  @Override
  public NodeEntry parseEntry(String e) {
    String content = e.substring(enrPrefix.length());
    List<DnsNode> nodeList = DnsNode.decompress(content);
    return new NodeEntry(nodeList);
  }

  @Override
  public String toString() {
    return null;
  }
}
