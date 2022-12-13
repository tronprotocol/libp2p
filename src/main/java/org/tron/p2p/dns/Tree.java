package org.tron.p2p.dns;

import lombok.Data;
import org.tron.p2p.protos.Connect;
import org.tron.p2p.protos.Discover;

import java.util.ArrayList;
import java.util.List;

@Data
public class Tree {
  private Tree father;
  private List<Tree> children;
  private Discover.DnsNode dnsNode;

  public Tree(Tree father, Discover.DnsNode dnsNode) {
    this.father = father;
    this.dnsNode = dnsNode;
    this.children = new ArrayList<>();
  }
}
