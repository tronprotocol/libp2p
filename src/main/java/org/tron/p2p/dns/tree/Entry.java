package org.tron.p2p.dns.tree;


public interface Entry {

  String rootPrefix = "enrtree-root:v1";
  String linkPrefix = "enrtree://";
  String branchPrefix = "enrtree-branch:";
  String enrPrefix = "enr:";

  Entry parseEntry(String e);

  String toString();
}
