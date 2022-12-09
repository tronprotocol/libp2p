package org.tron.p2p.dns.tree;


import java.security.SignatureException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.web3j.crypto.Hash;

@Slf4j(topic = "net")
public class RootEntry implements Entry {

  @Getter
  private String eRoot;
  @Getter
  private String lRoot;
  @Getter
  private int seq;
  @Getter
  @Setter
  private byte[] signature;

  public RootEntry(String eRoot, String lRoot, int seq) {
    this.eRoot = eRoot;
    this.lRoot = lRoot;
    this.seq = seq;
  }

  public RootEntry(String eRoot, String lRoot, int seq, byte[] signature) {
    this.eRoot = eRoot;
    this.lRoot = lRoot;
    this.seq = seq;
    this.signature = signature;
  }

  public static RootEntry parseEntry(String e, String publicKey) throws SignatureException {
    log.info("Root url:[{}], public key:{}", e, publicKey);
    String[] items = e.split("\\s+");
    String eroot = items[1].split("=")[1];
    String lroot = items[2].split("=")[1];
    String sequence = items[3].split("=")[1];
    String sig = items[4].split("=")[1];

    String data = String.format("%s e=%s l=%s seq=%s", rootPrefix, eroot, lroot, sequence);
    byte[] signature = Algorithm.decode64(sig);
    if (signature.length != 65) {
      throw new SignatureException("invalid base64 signature: " + sig);
    }
    boolean verify = Algorithm.verifySignature(publicKey, data, signature);
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
    return String.format("%s e=%s l=%s seq=%d", Entry.rootPrefix, eRoot, lRoot, seq);
  }
}
