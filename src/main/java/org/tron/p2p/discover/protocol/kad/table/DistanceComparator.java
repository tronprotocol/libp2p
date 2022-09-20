package org.tron.p2p.discover.protocol.kad.table;

import java.util.Comparator;

public class DistanceComparator implements Comparator<NodeEntry> {
  private byte[] targetId;

  DistanceComparator(byte[] targetId) {
    this.targetId = targetId;
  }

  @Override
  public int compare(NodeEntry e1, NodeEntry e2) {
    int d1 = NodeEntry.distance(targetId, e1.getNode().getId());
    int d2 = NodeEntry.distance(targetId, e2.getNode().getId());

    if (d1 > d2) {
      return 1;
    } else if (d1 < d2) {
      return -1;
    } else {
      return 0;
    }
  }
}
