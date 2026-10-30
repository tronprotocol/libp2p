package org.tron.p2p.dns.sync;


import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.Algorithm;
import org.tron.p2p.dns.tree.BranchEntry;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.dns.tree.NodesEntry;
import org.tron.p2p.exception.DnsException;
import org.tron.p2p.exception.DnsException.TypeEnum;
import org.tron.p2p.utils.ByteArray;

public class ClientEntryValidationTest {

  private static final String DOMAIN = "nodes.example.invalid";

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
    String nodes = Entry.nodesPrefix + DnsNode.compress(Collections.singletonList(
        new DnsNode(null, "192.0.2.1", "2001:db8::1", 18888)));
    NodesEntry parsedNodes = (NodesEntry) Client.parseEntry(DOMAIN,
        Algorithm.encode32AndTruncate(nodes), nodes);
    Assert.assertEquals(1, parsedNodes.getNodes().size());
    Assert.assertEquals("192.0.2.1", parsedNodes.getNodes().get(0).getHostV4());
    Assert.assertEquals("2001:db8:0:0:0:0:0:1", parsedNodes.getNodes().get(0).getHostV6());

    String child = Algorithm.encode32AndTruncate(nodes);
    String branch = Entry.branchPrefix + child;
    BranchEntry parsedBranch = (BranchEntry) Client.parseEntry(DOMAIN,
        Algorithm.encode32AndTruncate(branch), branch);
    Assert.assertArrayEquals(new String[]{child}, parsedBranch.getChildren());

    String publicKey = Algorithm.encode32(ByteArray.fromHexString(
        Algorithm.compressPubKey(Algorithm.generateKeyPair("01").getPublicKey())));
    String link = LinkEntry.buildRepresent(publicKey, DOMAIN);
    LinkEntry parsedLink = (LinkEntry) Client.parseEntry(DOMAIN,
        Algorithm.encode32AndTruncate(link), link);
    Assert.assertEquals(DOMAIN, parsedLink.getDomain());
    Assert.assertEquals(link, parsedLink.toString());
  }

  private static void assertHashMismatch(String hash, String txt) {
    try {
      Client.parseEntry(DOMAIN, hash, txt);
      Assert.fail("Expected a hash mismatch");
    } catch (DnsException expected) {
      Assert.assertEquals(TypeEnum.HASH_MISS_MATCH, expected.getType());
    }
  }
}
