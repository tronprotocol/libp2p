package org.tron.p2p.dns.sync;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.tron.p2p.dns.DnsNode;


public class RandomIterator implements Iterator<DnsNode> {

  private Client client;
  @Getter
  private DnsNode cur;
  private LinkCache linkCache;
  private Map<String, ClientTree> trees;
  private List<ClientTree> syncAbleList;
  private List<ClientTree> disabledList;

  @Override
  public DnsNode next() {
    return null;
  }

  @Override
  public boolean hasNext() {
    return false;
  }

  public void addTree(String url) {
  }

  public ClientTree pickTree() {
    return null;
  }

  public List<ClientTree> syncAbleTrees() {
    return null;
  }

  public void waitForRootUpdates(List<ClientTree> trees) {
  }

  public void rebuildTrees() {
  }

}
