package org.tron.p2p.dns.sync;

import java.util.Map;
import java.util.Set;


public class LinkCache {

  Map<String, Set<String>> backrefs;
  boolean changed;

  public boolean isReferenced(String url) {
    return false;
  }

  public void addLink(String from, String to) {
  }

  public void resetLinks(String from, Set<String> keep) {
  }
}
