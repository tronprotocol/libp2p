package org.tron.p2p.dns;


import com.google.protobuf.InvalidProtocolBufferException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.tree.Algorithm;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;

public class TreeTest {

  public static DnsNode[] sampleNode() throws UnknownHostException {
    return new DnsNode[] {
        new DnsNode(null, "192.168.0.1", null, 10000),
        new DnsNode(null, "192.168.0.2", null, 10000),
        new DnsNode(null, "192.168.0.3", null, 10000),
        new DnsNode(null, "192.168.0.4", null, 10000),
        new DnsNode(null, "192.168.0.5", null, 10000),
        new DnsNode(null, "192.168.0.6", null, 10001),
        new DnsNode(null, "192.168.0.6", null, 10002),
        new DnsNode(null, "192.168.0.6", null, 10003),
        new DnsNode(null, "192.168.0.6", null, 10004),
        new DnsNode(null, "192.168.0.6", null, 10005),
        new DnsNode(null, "192.168.0.10", "fe80::0001", 10005),
        new DnsNode(null, "192.168.0.10", "fe80::0002", 10005),
        new DnsNode(null, null, "fe80::0001", 10000),
        new DnsNode(null, null, "fe80::0002", 10000),
        new DnsNode(null, null, "fe80::0002", 10001),
    };
  }

  @Test
  public void testMerge() throws UnknownHostException {
    DnsNode[] nodes = sampleNode();
    List<DnsNode> nodeList = Arrays.asList(nodes);

    List<String> enrs = Tree.merge(nodeList);
    int total = 0;
    for (int i = 0; i < enrs.size(); i++) {
      List<DnsNode> subList = null;
      try {
        subList = DnsNode.decompress(enrs.get(i).substring(Entry.enrPrefix.length()));
      } catch (InvalidProtocolBufferException e) {
        Assert.fail();
      }
      if (i < enrs.size() - 1) {
        Assert.assertEquals(Tree.mergeSize, subList.size());
      } else {
        Assert.assertTrue(subList.size() <= Tree.mergeSize);
      }
      total += subList.size();
    }
    Assert.assertEquals(nodeList.size(), total);
  }

  @Test
  public void testTreeBuild() throws UnknownHostException {
    int seq = 0;

    DnsNode[] dnsNodes = new DnsNode[] {
        new DnsNode(null, "192.168.0.1", null, 10000),
        new DnsNode(null, "192.168.0.2", null, 10000),
        new DnsNode(null, "192.168.0.3", null, 10000),
        new DnsNode(null, "192.168.0.4", null, 10000),
        new DnsNode(null, "192.168.0.5", null, 10000),
        new DnsNode(null, "192.168.0.6", null, 10000),
        new DnsNode(null, "192.168.0.7", null, 10000),
        new DnsNode(null, "192.168.0.8", null, 10000),
        new DnsNode(null, "192.168.0.9", null, 10000),
        new DnsNode(null, "192.168.0.10", null, 10000),

        new DnsNode(null, "192.168.0.11", null, 10000),
        new DnsNode(null, "192.168.0.12", null, 10000),
        new DnsNode(null, "192.168.0.13", null, 10000),
        new DnsNode(null, "192.168.0.14", null, 10000),
        new DnsNode(null, "192.168.0.15", null, 10000),
        new DnsNode(null, "192.168.0.16", null, 10000),
        new DnsNode(null, "192.168.0.17", null, 10000),
        new DnsNode(null, "192.168.0.18", null, 10000),
        new DnsNode(null, "192.168.0.19", null, 10000),
        new DnsNode(null, "192.168.0.20", null, 10000),

        new DnsNode(null, "192.168.0.21", null, 10000),
        new DnsNode(null, "192.168.0.22", null, 10000),
        new DnsNode(null, "192.168.0.23", null, 10000),
        new DnsNode(null, "192.168.0.24", null, 10000),
        new DnsNode(null, "192.168.0.25", null, 10000),
        new DnsNode(null, "192.168.0.26", null, 10000),
        new DnsNode(null, "192.168.0.27", null, 10000),
        new DnsNode(null, "192.168.0.28", null, 10000),
        new DnsNode(null, "192.168.0.29", null, 10000),
        new DnsNode(null, "192.168.0.30", null, 10000),

        new DnsNode(null, "192.168.0.31", null, 10000),
        new DnsNode(null, "192.168.0.32", null, 10000),
        new DnsNode(null, "192.168.0.33", null, 10000),
        new DnsNode(null, "192.168.0.34", null, 10000),
        new DnsNode(null, "192.168.0.35", null, 10000),
        new DnsNode(null, "192.168.0.36", null, 10000),
        new DnsNode(null, "192.168.0.37", null, 10000),
        new DnsNode(null, "192.168.0.38", null, 10000),
        new DnsNode(null, "192.168.0.39", null, 10000),
        new DnsNode(null, "192.168.0.40", null, 10000),
    };

    String[] enrs = new String[dnsNodes.length];
    for (int i = 0; i < dnsNodes.length; i++) {
      DnsNode dnsNode = dnsNodes[i];
      List<DnsNode> nodeList = new ArrayList<>();
      nodeList.add(dnsNode);
      enrs[i] = Entry.enrPrefix + DnsNode.compress(nodeList);
    }

    String[] links = new String[] {};

    String linkBranch0 = "enrtree-branch:";
    String enrBranch1 = "enrtree-branch:BLD3R3KMDTU45ZLWJVZUPTFYSU,VMR2V5OEVYEDMOBXQVCDXC27BQ,YGSUKDEJ4BIAHGAFOMD2FAFM6E,TS37BZBFM26FX6AQZYEG5AYHBI,TV32WATOVCUYJZGLAL4WZJFUDA,4TVT5IRPHA5LM44SUDV2ROKEAE,E2ONR5TTLGBEF7GAQPRTONEBL4,U4UWUP4P6VCBU67TGOF4DHOQKE,33MFG7RVRC222ZER2KUGFZZALI,YSLBZEKCJ3TMPZI5ZB4PBEOQ5U,LE35Q7ZYW7UKGBBUXMWYXI6RNM,VJHUAEGWOJJ3KLUYQSMTW5IN6M,R4CB53QODD75K77POLYSVHGARE";
    String enrBranch2 = "enrtree-branch:OXJM7ILGB7R5KPFWHGE4WWVDFM,OUQU7CPV3WINGXARXPE6BUWMHI,VHKMLLMOTBRVGWHCRG7J4MKADQ,WWW7CA42SVYJRBBAGJLJC53HRU,DLHMH4WTZVTAODPL4USLFK4EFE,EY2FKZMS2VBALYF4BIYIOMLMOI,KPUZVWPB4G4CW4WUEBUEKKCFIY,K6ZIEIAMKUYLC5J7735L73GIOY,O3R2HTMZQVGNZH5QWI3N7NJOGM,OCFMB3TXJL3A3HMWB4LBRMKND4,MPRENJ6QJAVBCKQTSDXC7VSLGM,XSAKPV6THRFVRFY5ICAPMEVW2U,225YUW2SUVFPNBZGF6U4MRFLSM";
    String enrBranch3 = "enrtree-branch:J7ZZPDS32SYGAY4FE2OIV75ALE,W3NN3665O73SL3UX75CPQIGMKI,RK2MLI4RXN7DAWTGFFYGCHH5AM,U6QXGZAJ4S6EM62O74BI4ES7PQ,MZDJBVYAWWJELPXM5GQNXIZG5I,347EXJRF4MABPI72RRZT5BGK2A,GO2HUZ7TWZSTEB63NQZPM5LNO4,2HC72FNU3RLYFW47KSWZZ3DWOU,O6OAX4RUW644PWVL2XUQOP4BTE,5UPCPCC7GK3XSGBMYL4HDIGWSA,N3DRVRVWDC47FQFZEKCVM255ZU,D62KHBUZEIWEMNT7LLIZ3U3BUY,NPQ57QFBR2VICN4TIYZR6IPQMM";
    String enrBranch4 = "enrtree-branch:SWTFTTLY6CSP7NWCDFRXPQ5AKU,B7UJ5TNENXPH57QZSCMXRDOGR4,56TQVLKSN2WYDDNAHQ6IUNTFXE,S2M5KHI6LYLZNCPCLT76KLYGBE";

    String[] branches = new String[] {linkBranch0, enrBranch1, enrBranch2, enrBranch3, enrBranch4};

    List<String> branchList = Arrays.asList(branches);
    List<String> enrList = Arrays.asList(enrs);
    List<String> linkList = Arrays.asList(links);

//    Tree.sortByString(enrList);//eth use sortByID, but we sort by string
//    Tree.sortByString(linkList);

    Tree tree = new Tree();
    try {
      tree = tree.makeTree(seq, enrList, linkList, null);
    } catch (DnsException e) {
      e.printStackTrace();
      Assert.fail();
    }

    /*
                                 b r a n c h 4
                   /              /           \          \
                /               /               \           \
             /                /                   \            \
          branch1           branch2              branch3          \
        /      \          /       \             /       \           \
      enr:-01 ~ enr:-13  enr:-14 ~ enr:-26   enr:-27 ~ enr:-39  enr:-40
    */

    Assert.assertEquals(branchList.size() + enrList.size() + linkList.size(),
        tree.getEntries().size());
    Assert.assertEquals(branchList.size(), tree.getBranchesEntry().size());
    Assert.assertEquals(enrList.size(), tree.getNodesEntry().size());
    Assert.assertEquals(linkList.size(), tree.getLinksEntry().size());

    for (String branch : tree.getBranchesEntry()) {
      Assert.assertTrue(branchList.contains(branch));
    }
    for (String nodeEntry : tree.getNodesEntry()) {
      Assert.assertTrue(enrList.contains(nodeEntry));
    }
    for (String link : tree.getLinksEntry()) {
      Assert.assertTrue(linkList.contains(link));
    }

    Assert.assertEquals(Algorithm.encode32AndTruncate(enrBranch4), tree.getRootEntry().getERoot());
    Assert.assertEquals(Algorithm.encode32AndTruncate(linkBranch0), tree.getRootEntry().getLRoot());
    Assert.assertEquals(seq, tree.getSeq());
  }
}
