package org.tron.p2p.dns.tree;


public interface Entry {

  String rootPrefix = "enrtree-root:";
  String linkPrefix = "enrtree://";
  String branchPrefix = "enrtree-branch:";
  String enrPrefix = "enr:";

  //T parseEntry(String e) throws Exception;
}
