package org.tron.p2p.dns.sync;


import com.google.common.cache.CacheBuilder;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.dns.tree.RootEntry;
import org.tron.p2p.dns.tree.Tree;

public class Client {

  private Parameter parameter;
  private CacheBuilder entries;
  private Executor executor;

  public Client(Parameter parameter) {
    this.parameter = parameter;
    this.executor = Executors.newFixedThreadPool(5);
  }

  public Tree syncTree(String urlScheme) {
    return null;
  }

  private RootEntry resolveRoot(LinkEntry linkEntry) {
    return null;
  }

  private Entry resolveEntry(String domain, String hash) {
    return null;
  }

  private Entry doResolveEntry(String domain, String hash) {
    return null;
  }

  private RandomIterator newRandomIterator() {
    return null;
  }

  public Iterator newIterator() {
    return null;
  }
}
