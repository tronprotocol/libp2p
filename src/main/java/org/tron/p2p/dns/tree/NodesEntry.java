package org.tron.p2p.dns.tree;


import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.tron.p2p.dns.DnsNode;

public class NodesEntry implements Entry {

  private String represent;
  @Getter
  private List<DnsNode> nodes;

  public NodesEntry(String represent, List<DnsNode> nodes) {
    this.represent = represent;
    this.nodes = nodes;
  }

  public static NodesEntry parseEntry(String e) {
    //String content = e.substring(enrPrefix.length());
    //List<DnsNode> nodeList = DnsNode.decompress(content);
    //return new NodesEntry(e, nodeList);
    return new NodesEntry(e, new ArrayList<>());
  }

  @Override
  public String toString() {
    return represent;
  }
}
