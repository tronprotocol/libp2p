package org.tron.p2p.dns.tree;


import java.util.List;
import java.util.Map;
import org.tron.p2p.dns.DnsNode;

public class Tree {

  public static final int minHashLength = 12;

  private RootEntry rootEntry;
  private Map<String, Entry> entries;

  private Entry build(List<Entry> entries) {
    return null;
  }

  public Tree makeTree(int seq, List<String> nodes, List<String> links) {
    return null;
  }

  private static List<String> sortByIP(List<DnsNode> nodes) {
    return null;
  }

  public Map<String, String> toTXT(String rootDomain) {
    return null;
  }

  public String[] getLinks() {
    return null;
  }

  public DnsNode[] getNodes() {
    return null;
  }

  private LinkEntry sign(byte[] privateKey, String domain) {
    return null;
  }
}
