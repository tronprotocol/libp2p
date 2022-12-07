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
        return null;
      }
      DnsNode dnsNode;
      try {
        dnsNode = clientTree.syncRandom();
      } catch (Exception e) {
        log.info("Error in DNS random node sync, tree:{}, exception:{}",
            clientTree.linkEntry.getDomain(), e);
        continue;
      }
      cur = dnsNode;
      return dnsNode;
    }
  }

  @Override
  public boolean hasNext() {
    return false;
  }

  public void addTree(String url) {
    LinkEntry linkEntry = LinkEntry.parseEntry(url);
    if (linkEntry != null) {
      linkCache.addLink("", linkEntry.getRepresent());
    }
  }

  //the first random
  public ClientTree pickTree() {
    if (trees == null || trees.size() == 0) {
      return null;
    }
    if (linkCache.changed) {
      rebuildTrees();
      linkCache.changed = false;
    }
    boolean canSync = syncAbleTrees();
    if (canSync) {
      // Pick a random tree.
      int size = syncAbleList.size();
      return syncAbleList.get(random.nextInt(size));
    } else {
      // No sync action can be performed on any tree right now. The only meaningful
      // thing to do is waiting for any root record to get updated.
      waitForRootUpdates(disabledList);
    }
    // There are no trees left, the iterator was closed.
    return null;
  }

  public boolean syncAbleTrees() {
    syncAbleList = new ArrayList<>();
    disabledList = new ArrayList<>();
    for (ClientTree ct : trees.values()) {
      if (ct.canSyncRandom()) {
        syncAbleList.add(ct);
      } else {
        disabledList.add(ct);
      }
    }
    return syncAbleList.size() > 0;
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
    //todo
    //Thread.sleep(sleep);
  }

  private void rebuildTrees() {
    Iterator<Entry<String, ClientTree>> it = trees.entrySet().iterator();
    while (it.hasNext()) {
      Entry<String, ClientTree> entry = it.next();
      if (!linkCache.isReferenced(entry.getKey())) {
        it.remove();
      }
    }

    Iterator<Entry<String, Set<String>>> it2 = linkCache.backrefs.entrySet().iterator();
    while (it2.hasNext()) {
      Entry<String, Set<String>> entry = it2.next();
      String urlScheme = entry.getKey();
      if (!trees.containsKey(urlScheme)) {
        LinkEntry linkEntry = LinkEntry.parseEntry(urlScheme);
        trees.put(urlScheme, new ClientTree(client, linkCache, linkEntry));
      }
    }
  }

  public void close() {

  }

}
