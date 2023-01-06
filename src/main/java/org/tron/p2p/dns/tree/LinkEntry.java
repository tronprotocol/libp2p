package org.tron.p2p.dns.tree;


import java.math.BigInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.exception.DnsException;
import org.tron.p2p.exception.DnsException.TypeEnum;
import org.tron.p2p.utils.ByteArray;

@Slf4j(topic = "net")
public class LinkEntry implements Entry {

  @Getter
  private String represent;
  @Getter
  private String domain;
  @Getter
  private String unCompressPublicKey; //hex string

  public LinkEntry(String represent, String domain, String unCompressPublicKey) {
    this.represent = represent;
    this.domain = domain;
    this.unCompressPublicKey = unCompressPublicKey;
  }

  public LinkEntry(String domain, String unCompressPublicKey) {
    byte[] pubKey = ByteArray.fromHexString(unCompressPublicKey);
    this.represent = Algorithm.compressPubKey(new BigInteger(pubKey)) + "@" + domain;
    this.domain = domain;
    this.unCompressPublicKey = unCompressPublicKey;
  }

  public static LinkEntry parseEntry(String e) throws DnsException {
    if (!e.startsWith(linkPrefix)) {
      throw new DnsException(TypeEnum.INVALID_SCHEME_URL,
          "scheme url must starts with :[" + Entry.linkPrefix + "], but get " + e);
    }
    String[] items = e.substring(linkPrefix.length()).split("@");
    if (items.length != 2) {
      throw new DnsException(TypeEnum.NO_PUBKEY, "scheme url:" + e);
    }
    String base32PublicKey = items[0];

    try {
      byte[] data = Algorithm.decode32(base32PublicKey);
      String unCompressPublicKey = Algorithm.decompressPubKey(ByteArray.toHexString(data));
      return new LinkEntry(e, items[1], unCompressPublicKey);
    } catch (RuntimeException exception) {
      throw new DnsException(TypeEnum.BAD_PUBKEY, "bad public key:" + base32PublicKey);
    }
  }

  @Override
  public String toString() {
    return represent;
  }
}
