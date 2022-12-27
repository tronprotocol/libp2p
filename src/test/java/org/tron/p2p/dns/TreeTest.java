package org.tron.p2p.dns;


import com.google.protobuf.InvalidProtocolBufferException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
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
      Assert.assertTrue(subList.size() <= Tree.MaxMergeSize);
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
    String enrBranch1 = "enrtree-branch:N6GZW6KVOO67QLYOCQ6HHM6MMQ,LFQU6BOXHHP2FSBK2ECM6EDURU,SO3O6IIXXG56YMDTDBDWCS6PSY,FUG3POMRMU6NBPBB5SERNDJPVE,YGHASYY6FRNLKGRXFXEVEQGOPQ,7KMBIPOZKWA7EWIN4VODCAHNWI,KJP6AOXPRZMKB23TO73UNHC2WI,CVP3AVWZ3OT6JKUIH2HDMRQMEY,X7PMZVDIXPI6DMKDFF6W5YPEPI,5EA5DVCW2S67PG6JYFYTBKQTZA,TX5YUOMQA4X757ISOZFCR27EY4,AVQLVZ6FILHBA5DX4KX5FHEQOU,YHQNTBCXKYCANGJUREFDXT2XOI";
    String enrBranch2 = "enrtree-branch:SAPBAKPWQ467RTQZNTUHOPWAIM,KFJQJW36HSKNGUJCAC325JFB4U,HHLWY2IAXMT5HDKV3XDFSD6FWA,47PQNCEU7H7B5JVK3FDF7YOBSA,6Q7CW6IASHARTNPUTNQN6BJLNU,YTCK3DJECEX2ITE23BPR3VUXSA,TMMC44WWKH4VEK7H5G6SOR24LY,222AUPDMDJMERJX37ZJFD5YWUI,KTUTZUCN26K4MUC6C6W5342CXA,R2GBUDMUY6TWW55D33RHRPHMNQ,YKVZ4VVESPZIRK6NHK35V7FLQY,S6RTRAISE7MN2SQVRCTZSWRJR4,ELNPFWVU2XTPKAPQXJJZXWRCLM";
    String enrBranch3 = "enrtree-branch:BLD3R3KMDTU45ZLWJVZUPTFYSU,VMR2V5OEVYEDMOBXQVCDXC27BQ,YGSUKDEJ4BIAHGAFOMD2FAFM6E,TS37BZBFM26FX6AQZYEG5AYHBI,TV32WATOVCUYJZGLAL4WZJFUDA,4TVT5IRPHA5LM44SUDV2ROKEAE,E2ONR5TTLGBEF7GAQPRTONEBL4,U4UWUP4P6VCBU67TGOF4DHOQKE,33MFG7RVRC222ZER2KUGFZZALI,HU5XPWYRVBKN4WM5ARUX4Z7V74,A6GENVVG6HFMLO4XZ5UEIHXEJA,F67R7UI6QT3G3PLOI4NAV2TEWI,6IZO4OMN4KAFYHDWRUPLZEZYIY";
    String enrBranch4 = "enrtree-branch:PZYUY3VUQHAXYQEH25ZLMXXLTM,KK6J4EJUQQIWWJIJBTBVZGTD4Q,XT2DAWTYVHPJOJYVWJOYZNDP4Y,6DTCYQRHCH32NRIZHBDJEUEWEQ";

    String[] branches = new String[] {linkBranch0, enrBranch1, enrBranch2, enrBranch3, enrBranch4};

    List<String> branchList = Arrays.asList(branches);
    List<String> enrList = Arrays.asList(enrs);
    List<String> linkList = Arrays.asList(links);

    Tree tree = new Tree();
    try {
      tree = tree.makeTree(seq, enrList, linkList, null);
    } catch (DnsException e) {
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
    Assert.assertEquals(linkList.size(), tree.getLinks().size());

    for (String branch : tree.getBranchesEntry()) {
      Assert.assertTrue(branchList.contains(branch));
    }
    for (String nodeEntry : tree.getNodesEntry()) {
      Assert.assertTrue(enrList.contains(nodeEntry));
    }
    for (String link : tree.getLinks()) {
      Assert.assertTrue(linkList.contains(link));
    }

    Assert.assertEquals(Algorithm.encode32AndTruncate(enrBranch4), tree.getRootEntry().getERoot());
    Assert.assertEquals(Algorithm.encode32AndTruncate(linkBranch0), tree.getRootEntry().getLRoot());
    Assert.assertEquals(seq, tree.getSeq());
  }

  @Test
  public void testGroupAndMerge() throws UnknownHostException {
    Random random = new Random();
    //simulate some nodes
    int ipCount = 2000;
    List<DnsNode> dnsNodes = new ArrayList<>();
    Set<String> ipSet = new HashSet<>();
    int i = 0;
    while (i < ipCount) {
      i += 1;
      String ip = String.format("%d.%d.%d.%d", random.nextInt(256), random.nextInt(256),
          random.nextInt(256), random.nextInt(256));
      if (ipSet.contains(ip)) {
        continue;
      }
      ipSet.add(ip);
      dnsNodes.add(new DnsNode(null, ip, null, 10000));
    }
    Set<String> enrSet1 = new HashSet<>(Tree.merge(dnsNodes));
    System.out.println("srcSize:" + enrSet1.size());

    // delete some node
    int deleteCount = 100;
    i = 0;
    while (i < deleteCount) {
      i += 1;
      int deleteIndex = random.nextInt(dnsNodes.size());
      dnsNodes.remove(deleteIndex);
    }

    // add some node
    int addCount = deleteCount;
    i = 0;
    while (i < addCount) {
      i += 1;
      String ip = String.format("%d.%d.%d.%d", random.nextInt(256), random.nextInt(256),
          random.nextInt(256), random.nextInt(256));
      if (ipSet.contains(ip)) {
        continue;
      }
      ipSet.add(ip);
      dnsNodes.add(new DnsNode(null, ip, null, 10000));
    }
    Set<String> enrSet2 = new HashSet<>(Tree.merge(dnsNodes));

    // calculate changes
    Set<String> enrSet3 = new HashSet<>(enrSet2);
    enrSet3.removeAll(enrSet1); // enrSet2 - enrSet1
    System.out.println("addSize:" + enrSet3.size());
    Assert.assertTrue(enrSet3.size() < enrSet1.size());

    Set<String> enrSet4 = new HashSet<>(enrSet1);
    enrSet4.removeAll(enrSet2); //enrSet1 - enrSet2
    System.out.println("deleteSize:" + enrSet4.size());
    Assert.assertTrue(enrSet4.size() < enrSet1.size());

    Set<String> enrSet5 = new HashSet<>(enrSet1);
    enrSet5.retainAll(enrSet2); // enrSet1 && enrSet2
    System.out.println("intersectionSize:" + enrSet5.size());
    Assert.assertTrue(enrSet5.size() < enrSet1.size());
  }
}
