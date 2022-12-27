package org.tron.p2p.dns.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.exception.DnsException;


@Slf4j(topic = "net")
public class RandomIterator implements Iterator<DnsNode> {

  private Client client;
  private Map<String, ClientTree> trees;

  @Getter
  private DnsNode cur;
  private LinkCache linkCache;

  private List<ClientTree> syncAbleList;
  private List<ClientTree> disabledList;

  private Random random;

  public RandomIterator(Client client) {
    this.client = client;
    trees = new HashMap<>();
    linkCache = new LinkCache();
    random = new Random();
  }

  //syncs random tree entries until it finds a node.
  @Override
  public DnsNode next() {
    while (true) {
      ClientTree clientTree = pickTree();
      if (clientTree == null) {
        log.error("clientTree is null");
        return null;
      }
      DnsNode dnsNode;
      try {
        dnsNode = clientTree.syncRandom();
      } catch (Exception e) {
        log.info("Error in DNS random node sync, tree:{}, exception:{}",
            clientTree.getLinkEntry().getDomain(), e);
        continue;
      }
      if (dnsNode != null) {
        return dnsNode;
      }
    }
  }

  @Override
  public boolean hasNext() {
    this.cur = next();
    return this.cur != null;
  }

  public void addTree(String url) throws DnsException {
    LinkEntry linkEntry = LinkEntry.parseEntry(url);
    linkCache.addLink("", linkEntry.getRepresent());
    log.info("linkCache.backrefs size :{}", linkCache.backrefs.size());
    log.info("changes: {}", linkCache.isChanged());
  }

  //the first random
  private ClientTree pickTree() {
    if (trees == null) {
      log.info("trees is null");
      return null;
    }
    if (linkCache.isChanged()) {
      rebuildTrees();
      linkCache.setChanged(false);
    }
    boolean canSync = existSyncAbleTrees();
    if (canSync) {
      // Pick a random tree.
      int size = syncAbleList.size();
      return syncAbleList.get(random.nextInt(size));
    } else {
      // No sync action can be performed on any tree right now. The only meaningful
      // thing to do is waiting for any root record to get updated.
      if (disabledList.size() > 0) {
        waitForRootUpdates(disabledList);
      }
    }
    // There are no trees left, the iterator was closed.
    //return null;
    int size = disabledList.size();
    return disabledList.get(random.nextInt(size));
  }

  private boolean existSyncAbleTrees() {
    syncAbleList = new ArrayList<>();
    disabledList = new ArrayList<>();
    for (ClientTree ct : trees.values()) {
      if (ct.canSyncRandom()) {
        syncAbleList.add(ct);
      } else {
        disabledList.add(ct);
      }
    }
    return !syncAbleList.isEmpty();
  }

  private void waitForRootUpdates(List<ClientTree> waitTrees) {
    ClientTree ct = null;
    long nextCheck = Long.MAX_VALUE;
    for (ClientTree clientTree : waitTrees) {
      long check = clientTree.nextScheduledRootCheck();
      if (ct == null || check < nextCheck) {
        ct = clientTree;
        nextCheck = check;
      }
    }
    long sleep = nextCheck - System.currentTimeMillis();
    log.info("DNS iterator waiting for root updates, sleep:{}, tree:{}", sleep,
        ct.linkEntry.getDomain());
//    try {
//      Thread.sleep(sleep);
//    } catch (InterruptedException e) {
//    }
    for (ClientTree clientTree : waitTrees) {
      clientTree.setRoot(null);
    }
  }

  // rebuilds the 'trees' map.
  // if tree in trees is not referenced by other, wo delete it from trees and add it later.
  private void rebuildTrees() {
    log.info("rebuildTrees...");
    Iterator<Entry<String, ClientTree>> it = trees.entrySet().iterator();
    while (it.hasNext()) {
      Entry<String, ClientTree> entry = it.next();
      String urlScheme = entry.getKey();
      if (!linkCache.isReferenced(urlScheme)) {
        log.info("remove tree from trees:{}", urlScheme);
        it.remove();
      }
    }

    Iterator<Entry<String, Set<String>>> it2 = linkCache.backrefs.entrySet().iterator();
    while (it2.hasNext()) {
      Entry<String, Set<String>> entry = it2.next();
      String urlScheme = entry.getKey();
      if (!trees.containsKey(urlScheme)) {
        try {
          LinkEntry linkEntry = LinkEntry.parseEntry(urlScheme);
          trees.put(urlScheme, new ClientTree(client, linkCache, linkEntry));
          log.info("add tree to trees:{}", urlScheme);
        } catch (DnsException e) {
          log.error("Parse LinkEntry failed", e);
          continue;
        }
      }
    }
  }

  public void close() {
    trees = null;
  }
}
