package org.tron.p2p.dns.sync;


import java.net.UnknownHostException;
import java.security.SignatureException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.dns.tree.NodesEntry;
import org.tron.p2p.dns.tree.RootEntry;
import org.tron.p2p.exception.DnsException;
import org.xbill.DNS.TextParseException;

@Slf4j(topic = "net")
public class ClientTree {

  private static final int rootRecheckFailCount = 5;
  // used for construct
  private Client client;
  @Getter
  public LinkEntry linkEntry;
  private LinkCache linkCache;

  // used for check
  private long lastValidateTime;
  private int leafFailCount = 0;
  private int rootFailCount = 0;

  // used for sync
  @Getter
  @Setter
  private RootEntry root;
  private SubtreeSync enrs;
  private SubtreeSync links;

  //all links in this tree
  private Set<String> curLinks;
  private String linkGCRoot;

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

  // retrieves a single entry of the tree. The Node return value is non-nil if the entry was a node.
  public DnsNode syncRandom()
      throws DnsException, SignatureException, InterruptedException, TextParseException, UnknownHostException {
    if (rootUpdateDue()) {
      updateRoot();
    }

    // Link tree sync has priority, run it to completion before syncing ENRs.
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

    // Sync next random entry in ENR tree. Once every node has been visited, we simply
    // start over. This is fine because entries are cached internally by the client LRU
    // also by DNS resolvers.
    if (enrs.done()) {
      enrs = new SubtreeSync(client, linkEntry, root.getERoot(), false);
    }
    return syncNextRandomNode();
  }

  // checks if any meaningful action can be performed by syncRandom.
  public boolean canSyncRandom() {
    return rootUpdateDue() || !links.done() || !enrs.done() || enrs.leaves == 0;
  }

  // gcLinks removes outdated links from the global link cache. GC runs once when the link sync finishes.
  public void gcLinks() {
    if (!links.done() || root.getLRoot().equals(linkGCRoot)) {
      return;
    }
    linkCache.resetLinks(linkEntry.getRepresent(), curLinks);
    linkGCRoot = root.getLRoot();
  }

  // traversal next link of missing
  public void syncNextLink() throws DnsException, TextParseException, UnknownHostException {
    String hash = links.missing.peek();
    Entry entry = links.resolveNext(hash);
    links.missing.poll();

    if (entry instanceof LinkEntry) {
      LinkEntry dest = (LinkEntry) entry;
      linkCache.addLink(linkEntry.getRepresent(), dest.getRepresent());
      curLinks.add(dest.getRepresent());
    }
  }

  // get one hash from enr missing randomly, then get random node from hash if hash is a leaf node
  private DnsNode syncNextRandomNode()
      throws DnsException, TextParseException, UnknownHostException {
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
  private void updateRoot() throws TextParseException, DnsException, SignatureException,
      InterruptedException, UnknownHostException {
    log.info("updateRoot {}", linkEntry.getDomain());
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
    boolean tooManyFailures = leafFailCount > rootRecheckFailCount;
    boolean scheduledCheck = System.currentTimeMillis() > nextScheduledRootCheck();
    return root == null || tooManyFailures || scheduledCheck;
  }

  public long nextScheduledRootCheck() {
    return lastValidateTime + Client.recheckInterval * 1000L;
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
