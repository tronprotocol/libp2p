package org.tron.p2p.dns.tree;


import java.security.SignatureException;
import org.apache.commons.lang3.StringUtils;
import org.web3j.crypto.Hash;

public class RootEntry {

  private String eRoot;
  private String lRoot;
  private int seq;
  private byte[] signature;

  public RootEntry(String eRoot, String lRoot, int seq, byte[] signature) {
    this.eRoot = eRoot;
    this.lRoot = lRoot;
    this.seq = seq;
    this.signature = signature;
  }

  public RootEntry parseEntry(String e, String publicKey) {
    String[] items = e.split("\\s+");
    String eroot = items[1].split("=")[1];
    String lroot = items[2].split("=")[1];
    String sequence = items[3].split("=")[1];
    String sig = items[4].split("=")[1];

    String data = String.format("%s e=%s l=%s seq=%d", Entry.rootPrefix, eroot, lroot, sequence);
    byte[] signature = Algorithm.decode64(sig);

    boolean verify;
    try {
      verify = Algorithm.verifySignature(publicKey, data, signature);
    } catch (SignatureException ex) {
      return null;
    }
    if (!verify) {
      return null;
    }
    if (Algorithm.isValidHash(eroot) && Algorithm.isValidHash(lroot) && StringUtils.isNumeric(
        sequence)) {
      return new RootEntry(eroot, lroot, Integer.parseInt(sequence), signature);
    }
    return null;
  }

  public byte[] sigHash() {
    String data = String.format("%s e=%s l=%s seq=%d", Entry.rootPrefix, eRoot, lRoot, seq);
    return Hash.sha3(data.getBytes());
  }

  @Override
  public String toString() {
    return null;
  }
}
