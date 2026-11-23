package org.tron.p2p.dns.sync;

import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.dns.tree.RootEntry;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;
import org.tron.p2p.exception.DnsException.TypeEnum;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.TXTRecord;

public class ClientRootTest {

  private static final String DOMAIN = "nodes.example.invalid";

  @Test
  public void rootCoexistsWithVerificationTxtInEitherOrder() throws Exception {
    Tree tree = tree("01");
    String root = tree.getRootEntry().toFormat();
    TXTRecord verification = txt("site-verification=abc");
    TXTRecord candidate = txt(root);
    for (Record[] records : new Record[][]{{candidate, verification}, {verification, candidate}}) {
      Assert.assertEquals(root, Client.parseRoot(link(tree), records).toFormat());
    }
  }

  @Test
  public void invalidRootsDoNotHideAValidRoot() throws Exception {
    Tree tree = tree("01");
    RootEntry badSignature = tree("01").getRootEntry();
    byte[] signature = new byte[65];
    signature[64] = 42;
    badSignature.setSignature(signature);
    TXTRecord valid = txt(tree.getRootEntry().toFormat());
    for (TXTRecord invalid : new TXTRecord[]{txt(Entry.rootPrefix + "!"),
        txt(Entry.rootPrefix + "AA"), txt(badSignature.toFormat()),
        txt(tree("02").getRootEntry().toFormat())}) {
      for (Record[] records : new Record[][]{{invalid, valid}, {valid, invalid}}) {
        Assert.assertEquals(tree.getRootEntry().toFormat(),
            Client.parseRoot(link(tree), records).toFormat());
      }
    }
  }

  @Test
  public void missingLookupAndNoUsableRootRemainDistinct() throws Exception {
    LinkEntry link = link(tree("01"));
    Assert.assertEquals(TypeEnum.LOOK_UP_ROOT_FAILED,
        Assert.assertThrows(DnsException.class, () -> Client.parseRoot(link, null)).getType());
    for (Record[] records : new Record[][]{new Record[0], {txt("site-verification=abc")},
        {txt(Entry.rootPrefix + "!")}, {txt(tree("02").getRootEntry().toFormat())}}) {
      Assert.assertEquals(TypeEnum.NO_ROOT_FOUND,
          Assert.assertThrows(DnsException.class, () -> Client.parseRoot(link, records)).getType());
    }
    DnsException failure = Assert.assertThrows(DnsException.class,
        () -> Client.parseRoot(link, new Record[]{txt(Entry.rootPrefix + "!")}));
    Assert.assertTrue(failure.getMessage().contains(TypeEnum.NO_ROOT_FOUND.getDesc()));
    Assert.assertTrue(failure.getCause() instanceof IllegalArgumentException);
  }

  private static Tree tree(String key) throws Exception {
    Tree tree = new Tree();
    tree.makeTree(1, Collections.emptyList(), Collections.emptyList(), key);
    return tree;
  }

  private static LinkEntry link(Tree tree) throws Exception {
    return LinkEntry.parseEntry(LinkEntry.buildRepresent(tree.getBase32PublicKey(), DOMAIN));
  }

  private static TXTRecord txt(String value) throws Exception {
    return new TXTRecord(Name.fromString(DOMAIN + "."), DClass.IN, 600, value);
  }
}
