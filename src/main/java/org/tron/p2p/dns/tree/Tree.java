package org.tron.p2p.dns.tree;


import com.google.protobuf.InvalidProtocolBufferException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.exception.DnsException;

@Slf4j(topic = "net")
public class Tree {

  public static final int hashAbbrevSize = 1 + 16 * 13 / 8; // Size of an encoded hash (plus comma)
  public static final int maxChildren = 370 / hashAbbrevSize; // 13 children
  public static final int minHashLength = 12;
  public static final int mergeSize = 5;

  @Getter
  @Setter
  private RootEntry rootEntry;
  @Getter
  private Map<String, Entry> entries;

  public Tree() {
    this.entries = new HashMap<>();
  }

  private Entry build(List<Entry> leafs) {
    if (leafs.size() == 1) {
      return leafs.get(0);
    }
    if (leafs.size() <= maxChildren) {
      String[] children = new String[leafs.size()];
      for (int i = 0; i < leafs.size(); i++) {
        String subDomain = Algorithm.encode32AndTruncate(leafs.get(i).toString());
        children[i] = subDomain;
        this.entries.put(subDomain, leafs.get(i));
      }
      return new BranchEntry(children);
    }

    //every batch size of leaf entry construct a branch
    List<Entry> subtrees = new ArrayList<>();
    while (leafs.size() > 0) {
      int total = leafs.size();
      int n = Math.min(maxChildren, total);
      Entry branch = build(leafs.subList(0, n));

      leafs = leafs.subList(n, total);
      subtrees.add(branch);

      String subDomain = Algorithm.encode32AndTruncate(branch.toString());
      this.entries.put(subDomain, branch);
    }
    return build(subtrees);
  }

  public Tree makeTree(int seq, List<String> nodes, List<String> links, String privateKey)
      throws DnsException {
    List<Entry> nodesEntryList = new ArrayList<>();
    for (int i = 0; i < nodes.size(); i++) {
      nodesEntryList.add(NodesEntry.parseEntry(nodes.get(i)));
    }

    List<Entry> linkEntryList = new ArrayList<>();
    for (int i = 0; i < links.size(); i++) {
      linkEntryList.add(LinkEntry.parseEntry(links.get(i)));
    }

    Tree tree = new Tree();

    Entry eRoot = tree.build(nodesEntryList);
    String eRootStr = Algorithm.encode32AndTruncate(eRoot.toString());
    tree.getEntries().put(eRootStr, eRoot);

    Entry lRoot = tree.build(linkEntryList);
    String lRootStr = Algorithm.encode32AndTruncate(lRoot.toString());
    tree.getEntries().put(lRootStr, lRoot);

    tree.setRootEntry(new RootEntry(eRootStr, lRootStr, seq));
    // we will sign the tree later
    if (StringUtils.isNotEmpty(privateKey)) {
      byte[] sig = Algorithm.sigData(tree.rootEntry.toString(), privateKey);
      tree.rootEntry.setSignature(sig);
    }

    return tree;
  }

  public static List<String> merge(List<DnsNode> nodes) {
    Collections.sort(nodes);
    List<String> enrs = new ArrayList<>();
    while (nodes.size() >= mergeSize) {
      List<DnsNode> sub = nodes.subList(0, mergeSize);
      enrs.add(DnsNode.compress(sub));
      nodes = nodes.subList(mergeSize, nodes.size());
    }
    if (nodes.size() > 0) {
      enrs.add(DnsNode.compress(nodes));
    }
    return enrs;
  }

  //use for test
  public static void sortByString(List<String> nodes) {
    Collections.sort(nodes);
  }

  public Map<String, String> toTXT(String rootDomain) {
    Map<String, String> dnsRecords = new HashMap<>();
    for (String key : entries.keySet()) {
      String newKey = StringUtils.isNoneEmpty(rootDomain) ? key + "." + rootDomain : key;
      dnsRecords.put(newKey, entries.get(key).toString());
    }
    return dnsRecords;
  }

  public int seq() {
    return rootEntry.getSeq();
  }

  public String signature() {
    return Algorithm.encode64(rootEntry.getSignature());
  }

  public List<String> getLinksEntry() {
    List<String> links = new ArrayList<>();
    for (String hash : entries.keySet()) {
      Entry entry = entries.get(hash);
      if (entry instanceof LinkEntry) {
        LinkEntry linkEntry = (LinkEntry) entry;
        links.add(linkEntry.toString());
      }
    }
    return links;
  }

  public List<String> getBranchesEntry() {
    List<String> branches = new ArrayList<>();
    for (String hash : entries.keySet()) {
      Entry entry = entries.get(hash);
      if (entry instanceof BranchEntry) {
        BranchEntry branchEntry = (BranchEntry) entry;
        branches.add(branchEntry.toString());
      }
    }
    return branches;
  }

  public List<String> getNodesEntry() {
    List<String> nodesEntryList = new ArrayList<>();
    for (String hash : entries.keySet()) {
      Entry entry = entries.get(hash);
      if (entry instanceof NodesEntry) {
        NodesEntry nodesEntry = (NodesEntry) entry;
        nodesEntryList.add(nodesEntry.toString());
      }
    }
    return nodesEntryList;
  }

  public List<DnsNode> getNodes() {
    List<String> nodesEntryList = getNodesEntry();
    List<DnsNode> nodes = new ArrayList<>();
    for (String represent : nodesEntryList) {
      String jonStr = represent.substring(Entry.enrPrefix.length());
      List<DnsNode> subNodes;
      try {
        subNodes = DnsNode.decompress(jonStr);
      } catch (InvalidProtocolBufferException | UnknownHostException e) {
        log.error("", e);
        continue;
      }
      if (subNodes != null) {
        nodes.addAll(subNodes);
      }
    }
    return nodes;
  }

  private LinkEntry sign(String privateKey, String publicKey, String domain) {
    byte[] signature = Algorithm.sigData(rootEntry.toString(), privateKey);
    rootEntry.setSignature(signature);
    return new LinkEntry(domain, publicKey);
  }
}
