package org.tron.p2p.dns.sync;


import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.dns.tree.NodesEntry;
import org.tron.p2p.dns.tree.RootEntry;

public class ClientTree {

  private final int rootRecheckFailCount = 5;
  // used for construct
  public Client client;
  public LinkEntry linkEntry;
  public LinkCache linkCache;

  // used for check
  public long lastValidateTime;
  public int leafFailCount = 0;
  public int rootFailCount = 0;

  // used for sync
  public RootEntry root;
  public SubtreeSync enrs;
  public SubtreeSync links;

  //all links in this tree
  public Set<String> curLinks;
  public String linkGCRoot;

  private Random random;

  public ClientTree(Client c, LinkCache lc, LinkEntry loc) {
    this.client = c;
    this.linkCache = lc;
    this.linkEntry = loc;
    curLinks = new HashSet<>();
    random = new Random();
  }

  public void syncAll(Map<String, Entry> entries) throws Exception {
    updateRoot();
    links.resolveAll(entries);
    enrs.resolveAll(entries);
  }

  public DnsNode syncRandom() throws Exception {
    if (rootUpdateDue()) {
      updateRoot();
    }
    if (!links.done()) {
      try {
        syncNextLink();
      } catch (Exception e) {
        leafFailCount++;
        throw e;
      }
      return null;
    }
    gcLinks();

    if (enrs.done()) {
      enrs = new SubtreeSync(client, linkEntry, root.getERoot(), false);
    }
    return syncNextRandomNode();
  }

  // canSyncRandom checks if any meaningful action can be performed by syncRandom.
  public boolean canSyncRandom() {
    return rootUpdateDue() || !links.done() || !enrs.done() || enrs.leaves == 0;
  }

  // gcLinks removes outdated links from the global link cache. GC runs once when the link sync finishes.
  public void gcLinks() {
    if (!links.done() || root.getLRoot() == linkGCRoot) {
      return;
    }
    linkCache.resetLinks(linkEntry.getRepresent(), curLinks);
    linkGCRoot = root.getLRoot();
  }

  // traversal next link of missing
  public void syncNextLink() throws Exception {
    String hash = links.missing.peek();
    Entry entry = links.resolveNext(hash);
    links.missing.poll();

    if (entry instanceof LinkEntry) {
      LinkEntry dest = (LinkEntry) entry;
      linkCache.addLink(linkEntry.getRepresent(), dest.getRepresent());
      curLinks.add(dest.getRepresent());
    }
  }

  // the second random and the third random
  private DnsNode syncNextRandomNode() throws Exception {
    int pos = random.nextInt(enrs.missing.size());
    String hash = enrs.missing.get(pos);
    Entry entry = enrs.resolveNext(hash);
    enrs.missing.remove(pos);
    if (entry instanceof NodesEntry) {
      NodesEntry nodesEntry = (NodesEntry) entry;
      List<DnsNode> nodeList = nodesEntry.getNodes();
      int size = nodeList.size();
      return nodeList.get(random.nextInt(size));
    }
    return null;
  }


  // updateRoot ensures that the given tree has an up-to-date root.
  private void updateRoot() throws Exception {
    slowdownRootUpdate();
    lastValidateTime = System.currentTimeMillis();
    RootEntry rootEntry = client.resolveRoot(linkEntry);
    if (rootEntry == null) {
      rootFailCount += 1;
      return;
    }
    this.root = rootEntry;
    rootFailCount = 0;
    leafFailCount = 0;

    if (links == null || !rootEntry.getLRoot().equals(links.root)) {
      links = new SubtreeSync(client, linkEntry, rootEntry.getLRoot(), true);
      curLinks = new HashSet<>();//clear all links
    }
    if (enrs == null || !rootEntry.getERoot().equals(enrs.root)) {
      enrs = new SubtreeSync(client, linkEntry, rootEntry.getERoot(), false);
    }
  }

  private boolean rootUpdateDue() {
    boolean tooManyFailures = rootFailCount > rootRecheckFailCount;
    boolean scheduledCheck = nextScheduledRootCheck() > System.currentTimeMillis();
    return root == null || tooManyFailures || scheduledCheck;
  }

  public long nextScheduledRootCheck() {
    return lastValidateTime + Parameter.recheckInterval * 60 * 1000L;
  }

  private void slowdownRootUpdate() throws InterruptedException {
    if (rootFailCount > 20) {
      Thread.sleep(10 * 1000L);
    } else if (rootFailCount > 5) {
      Thread.sleep(5 * 1000L);
    }
  }

  public String toString() {
    return linkEntry.toString();
  }
}
