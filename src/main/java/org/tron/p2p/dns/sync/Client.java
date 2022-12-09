package org.tron.p2p.dns.sync;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import org.tron.p2p.exception.HashMissMatchException;
import org.tron.p2p.exception.NoRootException;
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
      tree.setRootEntry(clientTree.root);
      return tree;
    }
    return null;
  }

  public RootEntry resolveRoot(LinkEntry linkEntry) throws NoRootException, TextParseException {
    TXTRecord txtRecord = LookUpTxt.lookUpTxt(linkEntry.getDomain());
    RootEntry entry = null;
    for (String txt : txtRecord.getStrings()) {
      if (txt.startsWith(Entry.rootPrefix)) {
        try {
          entry = RootEntry.parseEntry(txt, linkEntry.getUnCompressPublicKey());
        } catch (SignatureException e) {
          throw new NoRootException(e);
        }
        break;
      }
    }
    return entry;
  }

  // resolveEntry retrieves an entry from the cache or fetches it from the network if it isn't cached.
  public Entry resolveEntry(String domain, String hash) throws HashMissMatchException {
    Entry entry = cache.getIfPresent(hash);
    if (entry != null) {
      return entry;
    }
    entry = doResolveEntry(domain, hash);
    return entry;
  }

  private Entry doResolveEntry(String domain, String hash) throws HashMissMatchException {
    try {
      ByteArray.toHexString(Algorithm.decode32(hash));
    } catch (Exception e) {
      log.error("invalid base32 hash: {}", hash);
      return null;
    }
    TXTRecord txtRecord;
    try {
      txtRecord = LookUpTxt.lookUpTxt(hash, domain);
    } catch (TextParseException e) {
      log.error("lookUpTxt hash:{} domain:{} failed", hash, domain);
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

    if (entry != null) {
      String wantHash = Algorithm.encode32AndTruncate(entry.toString());
      if (!wantHash.equals(hash)) {
        throw new HashMissMatchException(
            String.format("hash mismatch, want: [%s], really: [%s], content: [%s]", wantHash, hash,
                entry));
      }
    }
    return entry;
  }

  public RandomIterator newIterator(List<String> urlSchemes) {
    RandomIterator randomIterator = new RandomIterator(this);
    for (String urlScheme : urlSchemes) {
      randomIterator.addTree(urlScheme);
    }
    return randomIterator;
  }

  public static void main(String[] args) {
    Client client = new Client();
    try {
      Tree tree = client.syncTree(
          "enrtree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@les.mainnet.ethdisco.net");
      System.out.println("==branch==");
      for (String branch : tree.getBranchesEntry()) {
        System.out.println(branch);
      }
      System.out.println("==link==");
      for (String link : tree.getLinksEntry()) {
        System.out.println(link);
      }
      System.out.println("==node==");
      for (String node : tree.getNodesEntry()) {
        System.out.println(node);
      }
      System.out.println();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
