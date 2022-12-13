package org.tron.p2p.dns;

import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.protos.Connect;
import org.tron.p2p.protos.Discover;
import org.xerial.snappy.Snappy;

import java.io.IOException;
import java.util.Map;

@Slf4j(topic = "net")
public class Client {

  public static Discover.DnsRoot queryDnsRoot(Discover.DnsDomain dnsDomain) throws IOException {
    byte[] value = null;
    //1. nds interface get values
    Snappy.uncompress(value);
    Discover.DnsRoot dnsRoot = Discover.DnsRoot.parseFrom(value);
    //2. valid sign
    return dnsRoot;
  }

  public static Discover.DnsNode queryDnsNode(Discover.DnsDomain dnsDomain, String subKey) throws IOException {
    byte[] value = null;
    //1. nds interface get values
    Snappy.uncompress(value);
    //2. valid sha256
    Discover.DnsNode dnsRoot = Discover.DnsNode.parseFrom(value);
    return dnsRoot;
  }

  public static void updateDnsNode(Map<String, String> values){}

}
