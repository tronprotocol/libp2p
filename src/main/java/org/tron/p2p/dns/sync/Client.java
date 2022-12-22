package org.tron.p2p.dns.sync;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.UnknownHostException;
import java.security.SignatureException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
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

  private Cache<String, Entry> cache;
  private Executor resolveExecutor;

  public Client() {
    this.cache = CacheBuilder.newBuilder()
        .maximumSize(Parameter.cacheLimit)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .recordStats()
        .build();
    this.resolveExecutor = Executors.newFixedThreadPool(5);
  }

  public Tree syncTree(String urlScheme) throws Exception {
    LinkEntry loc = LinkEntry.parseEntry(urlScheme);
    if (loc != null) {
      ClientTree clientTree = new ClientTree(this, new LinkCache(), loc);
      Tree tree = new Tree();
      clientTree.syncAll(tree.getEntries());
      tree.setRootEntry(clientTree.getRoot());
      return tree;
    }
    return null;
  }

  public RootEntry resolveRoot(LinkEntry linkEntry)
      throws TextParseException, DnsException, SignatureException, UnknownHostException {
    TXTRecord txtRecord = LookUpTxt.lookUpTxt(linkEntry.getDomain());
    if (txtRecord == null) {
      throw new DnsException(TypeEnum.LOOK_UP_FAILED, "domain: " + linkEntry.getDomain());
    }
    for (String txt : txtRecord.getStrings()) {
      if (txt.startsWith(Entry.rootPrefix)) {
        return RootEntry.parseEntry(txt, linkEntry.getUnCompressPublicKey());
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

  public RandomIterator newIterator(List<String> urlSchemes) throws DnsException {
    RandomIterator randomIterator = new RandomIterator(this);
    for (String urlScheme : urlSchemes) {
      randomIterator.addTree(urlScheme);
    }
    return randomIterator;
  }
}
