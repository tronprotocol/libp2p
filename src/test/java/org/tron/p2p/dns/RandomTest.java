package org.tron.p2p.dns;


import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.dns.sync.Client;
import org.tron.p2p.dns.sync.RandomIterator;
import org.tron.p2p.dns.tree.Tree;

public class RandomTest {

  @Test
  public void testRandomIterator() {
    Parameter.p2pConfig = new P2pConfig();
    List<String> enrTreeUrls = new ArrayList<>();
    enrTreeUrls.add(
        "enrtree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@nile.nftderby1.net");
    enrTreeUrls.add(
        "enrtree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@shasta.nftderby1.net");
    Parameter.p2pConfig.setEnrTreeUrls(enrTreeUrls);

    Client syncClient = new Client();

    RandomIterator randomIterator = syncClient.newIterator();
    int count = 0;
    while (count < 10) {
      DnsNode dnsNode = randomIterator.next();
      Assert.assertNotNull(dnsNode);
      count += 1;
    }
  }
}
