package org.tron.p2p.dns.tree;


import org.bouncycastle.util.encoders.Base32;

public class LinkEntry implements Entry {

  private String domain;
  private byte[] compressPublicKey;

  public LinkEntry(String domain, byte[] compressPublicKey) {
    this.domain = domain;
    this.compressPublicKey = compressPublicKey;
  }

  @Override
  public LinkEntry parseEntry(String e) {
    String[] items = e.substring(linkPrefix.length()).split("@");
    String compressedPublicKey = items[0];
    return new LinkEntry(items[1], Base32.decode(compressedPublicKey));
  }

  @Override
  public String toString() {
    return null;
  }
}
