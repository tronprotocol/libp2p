package org.tron.p2p.discover.protocol.kad.table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tron.p2p.discover.Node;

public class NodeTable {
  private final Node node;  // our node
  private transient NodeBucket[] buckets;
  private transient Map<String, NodeEntry> nodes;

  public NodeTable(Node n) {
    this.node = n;
    initialize();
  }

  public Node getNode() {
    return node;
  }

  public final void initialize() {
    nodes = new HashMap<>();
    buckets = new NodeBucket[KademliaOptions.BINS];
    for (int i = 0; i < KademliaOptions.BINS; i++) {
      buckets[i] = new NodeBucket(i);
    }
  }

  public synchronized Node addNode(Node n) {
    if (n.getHost().equals(node.getHost())) {
      return null;
    }

    NodeEntry entry = nodes.get(n.getHost());
    if (entry != null) {
      entry.touch();
      return null;
    }

    NodeEntry e = new NodeEntry(node.getId(), n);
    NodeEntry lastSeen = buckets[getBucketId(e)].addNode(e);
    if (lastSeen != null) {
      return lastSeen.getNode();
    }
    nodes.put(n.getHost(), e);
    return null;
  }

  public synchronized void dropNode(Node n) {
    NodeEntry entry = nodes.get(n.getHost());
    if (entry != null) {
      nodes.remove(n.getHost());
      buckets[getBucketId(entry)].dropNode(entry);
    }
  }

  public synchronized boolean contains(Node n) {
    return nodes.containsKey(n.getHost());
  }

  public synchronized void touchNode(Node n) {
    NodeEntry entry = nodes.get(n.getHost());
    if (entry != null) {
      entry.touch();
    }
  }

  public int getBucketsCount() {
    int i = 0;
    for (NodeBucket b : buckets) {
      if (b.getNodesCount() > 0) {
        i++;
      }
    }
    return i;
  }

  public int getBucketId(NodeEntry e) {
    int id = e.getDistance() - 1;
    return id < 0 ? 0 : id;
  }

  public synchronized int getNodesCount() {
    return nodes.size();
  }

  public synchronized List<NodeEntry> getAllNodes() {
    return new ArrayList<>(nodes.values());
  }

  public synchronized List<Node> getClosestNodes(byte[] targetId) {
    List<NodeEntry> closestEntries = getAllNodes();
    List<Node> closestNodes = new ArrayList<>();
    Collections.sort(closestEntries, new DistanceComparator(targetId));
    if (closestEntries.size() > KademliaOptions.BUCKET_SIZE) {
      closestEntries = closestEntries.subList(0, KademliaOptions.BUCKET_SIZE);
    }
    for (NodeEntry e : closestEntries) {
      closestNodes.add(e.getNode());
    }
    return closestNodes;
  }

  public synchronized List<Node> getTableNodes() {
    List<Node> nodeList = new ArrayList<>();
    for (NodeEntry nodeEntry : nodes.values()) {
      nodeList.add(nodeEntry.getNode());
    }
    return nodeList;
  }
}
