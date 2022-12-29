package org.tron.p2p.dns.sync;


import com.google.protobuf.InvalidProtocolBufferException;
import java.net.UnknownHostException;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.BranchEntry;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.dns.tree.NodesEntry;
import org.tron.p2p.dns.tree.RootEntry;
import org.tron.p2p.exception.DnsException;
import org.xbill.DNS.TextParseException;

@Slf4j(topic = "net")
public class ClientTree {

  private static final int rootRecheckFailCount = 30 * 60;
  // used for construct
  private final Client client;
  @Getter
  @Setter
  private LinkEntry linkEntry;
  private final LinkCache linkCache;

  // used for check
  private long lastValidateTime;
  private int lastSeq = -1;
  private int leafFailCount = 0;
  private int rootFailCount = 0;

  // used for sync
  @Getter
  @Setter
  private RootEntry root;
  private SubtreeSync enrSync;
  private SubtreeSync linkSync;

  //all links in this tree
  private Set<String> curLinks;
  private String linkGCRoot;

  private final Random random;

  public ClientTree(Client c) {
    this.client = c;
    this.linkCache = new LinkCache();
    random = new Random();
  }

  public ClientTree(Client c, LinkCache lc, LinkEntry loc) {
    this.client = c;
    this.linkCache = lc;
    this.linkEntry = loc;
    curLinks = new HashSet<>();
    random = new Random();
  }

  public void syncAll(Map<String, Entry> entries) throws Exception {
    updateRoot(entries);
    linkSync.resolveAll(entries);
    enrSync.resolveAll(entries);
  }

  // retrieves a single entry of the tree. The Node return value is non-nil if the entry was a node.
  public DnsNode syncRandom()
      throws DnsException, SignatureException, InterruptedException, TextParseException, UnknownHostException, InvalidProtocolBufferException {
    if (rootUpdateDue()) {
      updateRoot(new HashMap<>());
    }

    // Link tree sync has priority, run it to completion before syncing ENRs.
    if (!linkSync.done()) {
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
    if (enrSync.done()) {
      enrSync = new SubtreeSync(client, linkEntry, root.getERoot(), false);
    }
    return syncNextRandomNode();
  }

  // checks if any meaningful action can be performed by syncRandom.
  public boolean canSyncRandom() {
    return rootUpdateDue() || !linkSync.done() || !enrSync.done() || enrSync.leaves == 0;
  }

  // gcLinks removes outdated links from the global link cache. GC runs once when the link sync finishes.
  public void gcLinks() {
    log.info("linkSync:{}, root:{}, linkGCRoot:{}", linkSync != null, root != null,
        linkGCRoot != null);
    //todo
    if (!linkSync.done() || root.getLRoot().equals(linkGCRoot)) {
      return;
    }
    linkCache.resetLinks(linkEntry.getRepresent(), curLinks);
    linkGCRoot = root.getLRoot();
  }

  // traversal next link of missing
  public void syncNextLink() throws DnsException, TextParseException, UnknownHostException {
    String hash = linkSync.missing.peek();
    Entry entry = linkSync.resolveNext(hash);
    linkSync.missing.poll();

    if (entry instanceof LinkEntry) {
      LinkEntry dest = (LinkEntry) entry;
      linkCache.addLink(linkEntry.getRepresent(), dest.getRepresent());
      curLinks.add(dest.getRepresent());
    }
  }

  // get one hash from enr missing randomly, then get random node from hash if hash is a leaf node
  private DnsNode syncNextRandomNode()
      throws DnsException, TextParseException, UnknownHostException {
    int pos = random.nextInt(enrSync.missing.size());
    String hash = enrSync.missing.get(pos);
    Entry entry = enrSync.resolveNext(hash);
    enrSync.missing.remove(pos);
    if (entry instanceof NodesEntry) {
      NodesEntry nodesEntry = (NodesEntry) entry;
      List<DnsNode> nodeList = nodesEntry.getNodes();
      int size = nodeList.size();
      return nodeList.get(random.nextInt(size));
    }
    return null;
  }

  // updateRoot ensures that the given tree has an up-to-date root.
  private void updateRoot(Map<String, Entry> entries)
      throws TextParseException, DnsException, SignatureException,
      InterruptedException, UnknownHostException, InvalidProtocolBufferException {
    log.info("UpdateRoot {}", linkEntry.getDomain());
    slowdownRootUpdate();
    lastValidateTime = System.currentTimeMillis();
    RootEntry rootEntry = client.resolveRoot(linkEntry);
    if (rootEntry == null) {
      rootFailCount += 1;
      return;
    }
    if (rootEntry.getSeq() <= lastSeq) {
      log.info("The seq of url doesn't change, url:[{}], seq:{}", linkEntry.getRepresent(),
          lastSeq);
      return;
    }

    root = rootEntry;
    lastSeq = rootEntry.getSeq();
    rootFailCount = 0;
    leafFailCount = 0;

    boolean clearLink = false;
    if (linkSync == null || !rootEntry.getLRoot().equals(linkSync.root)) {
      linkSync = new SubtreeSync(client, linkEntry, rootEntry.getLRoot(), true);
      curLinks = new HashSet<>();//clear all links

      //if entries comes from tree, we remove all LinkEntry
      Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
      while (it.hasNext()) {
        Entry entry = it.next().getValue();
        if (entry instanceof LinkEntry) {
          it.remove();
        }
      }
      clearLink = true;
    } else {
      // if lroot is not changed, wo do not to sync the link tree
      log.info("The lroot of url doesn't change, url:[{}], lroot:[{}]", linkEntry.getRepresent(),
          linkSync.root);
    }

    boolean clearEnr = false;
    if (enrSync == null || !rootEntry.getERoot().equals(enrSync.root)) {
      enrSync = new SubtreeSync(client, linkEntry, rootEntry.getERoot(), false);

      //if entries comes from tree, we remove all NodesEntry
      Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
      while (it.hasNext()) {
        Entry entry = it.next().getValue();
        if (entry instanceof NodesEntry) {
          it.remove();
        }
      }
      clearEnr = true;
    } else {
      // if eroot is not changed, wo do not to sync the enr tree
      log.info("The eroot of url doesn't change, url:[{}], eroot:[{}]", linkEntry.getRepresent(),
          enrSync.root);
    }

    if (clearLink && clearEnr) {
      Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
      while (it.hasNext()) {
        Entry entry = it.next().getValue();
        if (entry instanceof BranchEntry) {
          it.remove();
        }
      }
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
