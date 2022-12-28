package org.tron.p2p.dns.sync;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.UnknownHostException;
import java.security.SignatureException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.dns.lookup.LookUpTxt;
import org.tron.p2p.dns.tree.Algorithm;
import org.tron.p2p.dns.tree.BranchEntry;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.dns.tree.NodesEntry;
import org.tron.p2p.dns.tree.RootEntry;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;
import org.tron.p2p.exception.DnsException.TypeEnum;
import org.tron.p2p.utils.ByteArray;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;

@Slf4j(topic = "net")
public class Client {

  public final static int recheckInterval = 60; //seconds, should be smaller than rootTTL
  public final static int cacheLimit = 1000;
  private Cache<String, Entry> cache;
  @Getter
  private Map<String, Tree> trees;
  private Map<String, ClientTree> clientTrees;

  private ScheduledExecutorService syncer = Executors.newSingleThreadScheduledExecutor();

  public Client() {
    this.cache = CacheBuilder.newBuilder()
        .maximumSize(cacheLimit)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .recordStats()
        .build();
    trees = new HashMap<>();
    clientTrees = new HashMap<>();
  }

  public void init() {
    if (!Parameter.p2pConfig.getEnrTreeUrls().isEmpty()) {
      syncer.scheduleWithFixedDelay(() -> startSync(), 5, recheckInterval,
          TimeUnit.SECONDS);
    }
  }

  public void startSync() {
    for (String urlScheme : Parameter.p2pConfig.getEnrTreeUrls()) {
      ClientTree clientTree = clientTrees.getOrDefault(urlScheme, new ClientTree(this));
      Tree tree;
      try {
        tree = syncTree(urlScheme, clientTree);
      } catch (Exception e) {
        log.error("SyncTree failed, url:" + urlScheme, e);
        continue;
      }
      trees.put(urlScheme, tree);
      clientTrees.put(urlScheme, clientTree);
    }
  }

  public Tree syncTree(String urlScheme, ClientTree clientTree) throws Exception {
    LinkEntry loc = LinkEntry.parseEntry(urlScheme);
    if (loc != null) {
      if (clientTree == null) {
        clientTree = new ClientTree(this);
      }
      if (clientTree.getLinkEntry() == null) {
        clientTree.setLinkEntry(loc);
      }
      Tree tree = new Tree();
      clientTree.syncAll(tree.getEntries());
      tree.setRootEntry(clientTree.getRoot());
      log.info("SyncTree {} complete", urlScheme);
      return tree;
    }
    return null;
  }

  public RootEntry resolveRoot(LinkEntry linkEntry)
      throws TextParseException, DnsException, SignatureException, UnknownHostException {
    //do not put root in cache
    TXTRecord txtRecord = LookUpTxt.lookUpTxt(linkEntry.getDomain());
    if (txtRecord == null) {
      throw new DnsException(TypeEnum.LOOK_UP_FAILED, "domain: " + linkEntry.getDomain());
    }
    for (String txt : txtRecord.getStrings()) {
      if (txt.startsWith(Entry.rootPrefix)) {
        return RootEntry.parseEntry(txt, linkEntry.getUnCompressPublicKey(), linkEntry.getDomain());
      }
    }
    throw new DnsException(TypeEnum.NO_ROOT_FOUND, "domain: " + linkEntry.getDomain());
  }

  // resolveEntry retrieves an entry from the cache or fetches it from the network if it isn't cached.
  public Entry resolveEntry(String domain, String hash)
      throws DnsException, TextParseException, UnknownHostException {
    Entry entry = cache.getIfPresent(hash);
    if (entry != null) {
      return entry;
    }
    entry = doResolveEntry(domain, hash);
    if (entry != null) {
      cache.put(hash, entry);
    }
    return entry;
  }

  private Entry doResolveEntry(String domain, String hash)
      throws DnsException, TextParseException, UnknownHostException {
    try {
      ByteArray.toHexString(Algorithm.decode32(hash));
    } catch (Exception e) {
      throw new DnsException(TypeEnum.OTHER_ERROR, "invalid base32 hash: " + hash);
    }
    TXTRecord txtRecord = LookUpTxt.lookUpTxt(hash, domain);
    if (txtRecord == null) {
      return null;
    }
    String txt = LookUpTxt.joinTXTRecord(txtRecord);

    Entry entry = null;
    if (txt.startsWith(Entry.branchPrefix)) {
      entry = BranchEntry.parseEntry(txt);
    } else if (txt.startsWith(Entry.linkPrefix)) {
      entry = LinkEntry.parseEntry(txt);
    } else if (txt.startsWith(Entry.enrPrefix)) {
      entry = NodesEntry.parseEntry(txt);
    }

    if (entry == null) {
      throw new DnsException(TypeEnum.NO_ENTRY_FOUND,
          String.format("hash:%s, domain:%s, txt:%s", hash, domain, txt));
    }

    String wantHash = Algorithm.encode32AndTruncate(entry.toString());
    if (!wantHash.equals(hash)) {
      throw new DnsException(TypeEnum.HASH_MISS_MATCH,
          String.format("hash mismatch, want: [%s], really: [%s], content: [%s]", wantHash, hash,
              entry));
    }
    return entry;
  }

  public RandomIterator newIterator() {
    RandomIterator randomIterator = new RandomIterator(this);
    for (String urlScheme : Parameter.p2pConfig.getEnrTreeUrls()) {
      try {
        randomIterator.addTree(urlScheme);
      } catch (DnsException e) {
        log.error("AddTree failed " + urlScheme, e);
      }
    }
    return randomIterator;
  }

  public void close() {
    if (syncer != null) {
      syncer.shutdown();
    }
  }
}
