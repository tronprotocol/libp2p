package org.tron.p2p.dns.tree;


import com.google.protobuf.InvalidProtocolBufferException;
import java.security.SignatureException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.exception.DnsException;
import org.tron.p2p.exception.DnsException.TypeEnum;
import org.tron.p2p.protos.Discover.DnsRoot;
import org.tron.p2p.utils.ByteArray;

@Slf4j(topic = "net")
public class RootEntry implements Entry {

  private DnsRoot dnsRoot;
  @Getter
  private String eRoot;
  @Getter
  private String lRoot;
  @Getter
  @Setter
  private int seq;
  @Getter
  @Setter
  private byte[] signature;

  public RootEntry(DnsRoot dnsRoot) {
    this.dnsRoot = dnsRoot;
  }

  public RootEntry(String eRoot, String lRoot, int seq) {
    this.eRoot = eRoot;
    this.lRoot = lRoot;
    this.seq = seq;
  }

  public static RootEntry parseEntry(String e) throws DnsException {
    String[] items = e.split("\\s+");
//    DnsRoot dnsRoot1 = DnsRoot.parseFrom(ByteArray.fromHexString(items[1]));
    if (items.length != 5
        || items[1].split("=").length != 2
        || items[2].split("=").length != 2
        || items[3].split("=").length != 2
        || items[4].split("=").length != 2) {
      throw new DnsException(TypeEnum.INVALID_ROOT_SYNTAX, "data:[" + e + "]");
    }
    String eroot = items[1].split("=")[1];
    String lroot = items[2].split("=")[1];
    String sequence = items[3].split("=")[1];
    String sig = items[4].split("=")[1];

    if (!StringUtils.isNumeric(sequence)) {
      throw new DnsException(TypeEnum.OTHER_ERROR, "invalid seq");
    }
    RootEntry rootEntry = new RootEntry(eroot, lroot, Integer.parseInt(sequence));
    byte[] signature = Algorithm.decode64(sig);
    if (signature.length != 65) {
      throw new DnsException(TypeEnum.INVALID_SIGNATURE,
          String.format("signature's length(%d) != 65, signature: %s", signature.length,
              ByteArray.toHexString(signature)));
    }
    rootEntry.setSignature(signature);
    return rootEntry;
  }

  public static RootEntry parseEntry(String e, String publicKey, String domain)
      throws SignatureException, DnsException {
    log.info("Domain:{}, Root url:[{}], public key:{}", domain, e, publicKey);
    RootEntry rootEntry = parseEntry(e);
    boolean verify = Algorithm.verifySignature(publicKey, rootEntry.toString(),
        rootEntry.getSignature());
    if (!verify) {
      throw new DnsException(TypeEnum.INVALID_SIGNATURE,
          String.format("verify signature failed! data:[%s], publicKey:%s, domain:%s", e, publicKey,
              domain));
    }
    if (!Algorithm.isValidHash(rootEntry.eRoot) || !Algorithm.isValidHash(rootEntry.lRoot)) {
      throw new DnsException(TypeEnum.INVALID_CHILD,
          "eroot:" + rootEntry.eRoot + " lroot:" + rootEntry.lRoot);
    }
    return rootEntry;
  }

  @Override
  public String toString() {
    return String.format("%s e=%s l=%s seq=%d", Entry.rootPrefix, eRoot, lRoot, seq);
  }

  public String toFormat() {
    return String.format("%s sig=%s", toString(), Algorithm.encode64(signature));
  }
}
