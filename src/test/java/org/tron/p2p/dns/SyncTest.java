package org.tron.p2p.dns;


import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.dns.sync.Client;
import org.tron.p2p.dns.sync.ClientTree;
import org.tron.p2p.dns.tree.Tree;

public class SyncTest {

  @Test
  public void testSync() {
    Parameter.p2pConfig = new P2pConfig();
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add(
        "tree://APFGGTFOBVE2ZNAB3CSMNNX6RRK3ODIRLP2AA5U4YFAA6MSYZUYTQ@shasta.nftderby1.net");
    Parameter.p2pConfig.setTreeUrls(treeUrls);

    Client syncClient = new Client();

    ClientTree clientTree = new ClientTree(syncClient);
    Tree tree = new Tree();
    try {
      syncClient.syncTree(Parameter.p2pConfig.getTreeUrls().get(0), clientTree, tree);
    } catch (Exception e) {
      Assert.fail();
    }
  }
}
