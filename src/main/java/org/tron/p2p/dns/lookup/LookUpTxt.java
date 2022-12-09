package org.tron.p2p.dns.lookup;


import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

@Slf4j(topic = "net")
public class LookUpTxt {

  public static TXTRecord lookUpTxt(String hash, String domain) throws TextParseException {
    return lookUpTxt(hash + "." + domain);
  }

  //only get first Record
  public static TXTRecord lookUpTxt(String name) throws TextParseException {
    TXTRecord txt = null;
    log.info("LookUp name: {}", name);
    Record[] records = new Lookup(name, Type.TXT).run();
    for (int i = 0; i < records.length; i++) {
      txt = (TXTRecord) records[i];
    }
    return txt;
  }

  public static String joinTXTRecord(TXTRecord txtRecord) {
    StringBuffer sb = new StringBuffer();
    for (String s : txtRecord.getStrings()) {
      sb.append(s.trim());
    }
    return sb.toString();
  }

  public static void main(String[] args) throws TextParseException {
    lookUpTxt("all.mainnet.ethdisco.net");
    System.out.println(
        joinTXTRecord(lookUpTxt("5OY3KM2ZLKEEKOKFBNKLZSZADY", "les.mainnet.ethdisco.net")));
  }
}
