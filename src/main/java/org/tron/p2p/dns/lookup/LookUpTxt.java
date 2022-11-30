package org.tron.p2p.dns.lookup;


import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

public class LookUpTxt {

  public static TXTRecord lookUpTxt(String hash, String domain) throws TextParseException {
    return lookUpTxt(hash + "." + domain);
  }

  public static TXTRecord lookUpTxt(String name) throws TextParseException {
    TXTRecord txt = null;
    Record[] records = new Lookup(name, Type.TXT).run();
    for (int i = 0; i < records.length; i++) {
      txt = (TXTRecord) records[i];
    }
    return txt;
  }

  public static void main(String[] args) throws TextParseException {
    lookUpTxt("all.mainnet.ethdisco.net");
    lookUpTxt("B7JO7Z5HM6JTE7FHSGTZXNKYEU", "all.mainnet.ethdisco.net");
  }
}
