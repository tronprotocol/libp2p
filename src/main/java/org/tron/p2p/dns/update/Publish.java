package org.tron.p2p.dns.update;


import java.util.Map;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;

public interface Publish<T> {

  int rootTTL = 10 * 60;
  int treeNodeTTL = 4 * 7 * 24 * 60 * 60;

  double changeThreshold = 0.2;

  void deploy(String domainName, Tree t) throws DnsException;

  boolean deleteDomain(String domainName) throws Exception;

  Map<String, T> collectRecords(String domainName) throws Exception;
}
