package org.tron.p2p.dns.sync;


import java.util.Set;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.dns.tree.RootEntry;

public class ClientTree {

  //三个构造参数
  public Client c;
  public LinkEntry loc;
  public LinkCache lc;

  public long lastValidateTime;
  public int leafFailCount;
  public int rootFailCount;

  public RootEntry root;
  public SubtreeSync enrs;
  public SubtreeSync links;

  public Set<String> curLinks;
  public String linkGCRoot;

  public ClientTree(Client c, LinkCache lc, LinkEntry loc) {
    this.c = c;
    this.lc = lc;
    this.loc = loc;
  }

  public DnsNode syncRandom() {
    return null;
  }

  public boolean canSyncRandom() {
    return false;
  }

  public void gcLinks() {
  }

  public void syncNextLink() {
  }

  private DnsNode syncNextRandomNode() {
    return null;
  }

  public String toString() {
    return null;
  }

  private void updateRoot() {
  }

  private boolean rootUpdateDue() {
    return false;
  }

  private long nextScheduledRootCheck() {
    return 0;
  }

  private void slowdownRootUpdate() {
  }

}
