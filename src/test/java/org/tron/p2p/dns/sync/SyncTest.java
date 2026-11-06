package org.tron.p2p.dns.sync;


import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.dns.TreeTest;
import org.tron.p2p.dns.tree.Algorithm;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.NodesEntry;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;
import org.tron.p2p.exception.DnsException.TypeEnum;

public class SyncTest {

  private static final String DOMAIN = "nodes.example.invalid";

  @Test
  public void testSync() {
    Parameter.p2pConfig = new P2pConfig();
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add(
        "tree://AKMQMNAJJBL73LXWPXDI4I5ZWWIZ4AWO34DWQ636QOBBXNFXH3LQS@nile.trondisco.net");
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

  @Test
  public void mismatchedHashIsRejectedBeforeNodesPayloadIsDecoded() {
    assertHashMismatch(Algorithm.encode32AndTruncate("expected entry"), "nodes:!");
  }

  @Test
  public void hashMustMatchTheOriginalTxtRatherThanTheParsedBranch() {
    String branch = "tree-branch:" + Algorithm.encode32AndTruncate("child");
    // Branch parsing drops trailing empty children. It must not hide modified wire content.
    assertHashMismatch(Algorithm.encode32AndTruncate(branch), branch + ",");
  }

  @Test
  public void matchingHashesStillParseNodesBranchesAndLinks() throws Exception {
    Tree tree = TreeTest.signedTree(new Tree(), "192.0.2.1", "192.0.2.2");
    for (Entry expected : tree.getEntries().values()) {
      String txt = expected.toString();
      Entry actual = Client.parseEntry(DOMAIN, Algorithm.encode32AndTruncate(txt), txt);
      Assert.assertEquals(expected.getClass(), actual.getClass());
      Assert.assertEquals(txt, actual.toString());
      if (actual instanceof NodesEntry) {
        Assert.assertEquals(((NodesEntry) expected).getNodes(), ((NodesEntry) actual).getNodes());
      }
    }
  }

  private static void assertHashMismatch(String hash, String txt) {
    DnsException error = Assert.assertThrows(DnsException.class,
        () -> Client.parseEntry(DOMAIN, hash, txt));
    Assert.assertEquals(TypeEnum.HASH_MISS_MATCH, error.getType());
  }
}
