package org.tron.p2p.dns.lookup;


import java.net.InetAddress;
import java.net.UnknownHostException;
import lombok.extern.slf4j.Slf4j;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SimpleResolver;
import org.xbill.DNS.TXTRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

@Slf4j(topic = "net")
public class LookUpTxt {

  private static SimpleResolver defaultResolver() throws UnknownHostException {
    return new SimpleResolver(InetAddress.getByName("8.8.8.8"));
  }

  private static SimpleResolver backUpResolver() throws UnknownHostException {
    return new SimpleResolver(InetAddress.getByName("114.114.114.114"));
  }

  public static TXTRecord lookUpTxt(String hash, String domain)
      throws TextParseException, UnknownHostException {
    return lookUpTxt(hash + "." + domain);
  }

  //only get first Record
  public static TXTRecord lookUpTxt(String name) throws TextParseException, UnknownHostException {
    TXTRecord txt = null;
    log.info("LookUp name: {}", name);
    Lookup lookup = new Lookup(name, Type.TXT);
    lookup.setResolver(defaultResolver());
    Record[] records = lookup.run();
    if (records == null) {
      lookup.setResolver(backUpResolver());
      records = lookup.run();
    }
    if (records == null) {
      log.error("Failed to lookUp name:{}", name);
      return null;
    }
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
}
