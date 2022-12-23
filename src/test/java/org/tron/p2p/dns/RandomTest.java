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
    syncClient.init();

    try {
      Thread.sleep(10 * 1000);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }

    for (Tree tree : syncClient.getTrees().values()) {
      Assert.assertTrue(tree.getNodes().size() > 0);
    }

    RandomIterator randomIterator = syncClient.newIterator();
    int count = 0;
    while (count < 10) {
      DnsNode dnsNode = randomIterator.next();
      System.out.println(dnsNode == null);
      count += 1;
    }
  }
}
