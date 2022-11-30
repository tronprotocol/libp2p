package org.tron.p2p.dns.sync;


import java.util.Map;
import java.util.Queue;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.LinkEntry;

public class SubtreeSync {

  public Client client;
  public LinkEntry linkEntry;

  public String root;
  public Queue<String> missing;
  public boolean link;
  public int leaves = 0;

  public SubtreeSync(Client c, LinkEntry linkEntry, boolean link) {
  }

  public boolean done() {
    return false;
  }

  public void resolveAll(Map<String, Entry> dest) {
  }

  public Entry resolveNext(String hash) {
    return null;
  }
}
