package org.tron.p2p.dns;


import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.tree.Algorithm;
import org.tron.p2p.dns.tree.Tree;

public class TreeTest {

  @Test
  public void testTreeBuild() {
    int seq = 0;

    String[] enrs = new String[] {
        "enr:-11",
        "enr:-12",
        "enr:-13",
        "enr:-14",
        "enr:-15",
        "enr:-16",
        "enr:-17",
        "enr:-18",
        "enr:-19",
        "enr:-20",

        "enr:-01",
        "enr:-02",
        "enr:-03",
        "enr:-04",
        "enr:-05",
        "enr:-06",
        "enr:-07",
        "enr:-08",
        "enr:-09",
        "enr:-10",

        "enr:-31",
        "enr:-32",
        "enr:-33",
        "enr:-34",
        "enr:-35",
        "enr:-36",
        "enr:-37",
        "enr:-38",
        "enr:-39",
        "enr:-40",

        "enr:-21",
        "enr:-22",
        "enr:-23",
        "enr:-24",
        "enr:-25",
        "enr:-26",
        "enr:-27",
        "enr:-28",
        "enr:-29",
        "enr:-30",
    };

    String[] links = new String[] {};

    String linkBranch0 = "enrtree-branch:";
    String enrBranch1 = "enrtree-branch:6IO27O4VNVLEXOZGNSXQDKJXPY,3HNH3GNDZMNSAG2MW3U2CCI6Q4,GIB7THFXZA6KAR5VBZBKXL3ZOM,SI5I6TDMKLSMI2BYPFM4UBSNAY,56XW2HM5UL4VGGO35MEBL6EKH4,CEEWOCVSF5WKZJINLYCRDRGC5E,XFJF3RRL4VT4KUCUZSN6X237XA,7563TGAVUGLUS56UN4U6FPOALM,N5E37EIOZM5WKWPAJ4GWZBE6OM,S4TY4DRHH6H7BNON5PRQTKDMT4,IIJU5OHAOXF7U7NFRMIP4QWISY,LBTVYOOTPGHJ564ERWSQB3N3FE,2KZTZ2JGMGSSJZD36DIE756YFE";
    String enrBranch2 = "enrtree-branch:PSH6I7BLH34UPMZFVLIGGLK5Y4,RFKWDXZ7IHHG6P447AVDUMR47I,ROCOAI6W6BHP5Y7ZHSU4G2HWUE,J2GZ3YE5MGWAPJOLAFY3AK5QIQ,6TF3QO27XNKIFPWAHKJFNX3AAE,4IKISW3IVUFNOWSAHH76QZGDNU,TUIQUPD5Z72GEMATQRTSQQIIPM,OVPJK576RPLHQLYJMZTBYKTCZI,VPYADPNQ63XOAUGEC6XEGLTA3A,MFS4SS345VOTXX4AFAZCNBQXHE,U3WBA2RMWGUXUEE4QOQJWYOFIM,YBX4WAU5X5QY5ZI2UV6CBLALC4,FQF34URDWCXL666PAXN5VYF6NA";
    String enrBranch3 = "enrtree-branch:ULFQKWBT7IZWTJTPBFBTEHGYMA,ERRXKHBVFOX5RSURGVRCDKRQIY,A6JMATIOZFENSIDDHOSGY6SRIY,5UT3WSI7QYGSEFSPCJVG7TNXWQ,OJWI5NXHNVVPZLXM54C5W72T3U,UV655MTHW6D7KAAQVSEPARBM5E,JYWFLHVLZGNVZ34J3BJS7HZ7IQ,7J6D2VVC5P5TVLOGGW5HVUONBM,SMJVZUUZUTOEQ37A2ZMENGXFPU,TP5IVF3NEIUMNSO3CIPQFUOXRM,KJY3EWYENTGZBSHTK5PTGGQADQ,V4KYLTQ2SLDDUNHHQYUWUN674I,35IRJB63LLMAGQ2YBRLRCALKQA";
    String enrBranch4 = "enrtree-branch:IRUJBJM54QDEWPWFJAT7NQWQJ4,CCTZBAUKV5V4PEGUFZVMJ64Y4U,ADY23V3S6APLFAAG36HHEZ3X5M,HHA2E4PN3L4UB4N5PN2AH2VAS4";

    String[] branches = new String[] {linkBranch0, enrBranch1, enrBranch2, enrBranch3, enrBranch4};

    List<String> branchList = Arrays.asList(branches);
    List<String> enrList = Arrays.asList(enrs);
    List<String> linkList = Arrays.asList(links);

    Tree.sortByString(enrList);//eth use sortByID, but we sort by string
    Tree.sortByString(linkList);

    Tree tree = new Tree();
    tree = tree.makeTree(seq, enrList, linkList);

    /*
                                    b r a n c h 4
                 /              /            \              \
               /               /              \              \
             /                /                \              \
          branch1          branch2           branch3           \
        /      \         /       \          /       \           \
      enr:-01 ~ enr:13  enr:-14 ~ enr:26   enr:-27 ~ enr:-39  enr:40
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
    Assert.assertEquals(seq, tree.seq());
  }
}
