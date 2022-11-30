package org.tron.p2p.dns.tree;


public class BranchEntry implements Entry {

  private String[] children;

  public BranchEntry(String[] children) {
    this.children = children;
  }

  @Override
  public BranchEntry parseEntry(String e) {
    return new BranchEntry(e.substring(branchPrefix.length()).split(","));
  }

  @Override
  public String toString() {
    return null;
  }
}
