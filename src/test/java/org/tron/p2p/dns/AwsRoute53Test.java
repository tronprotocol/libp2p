package org.tron.p2p.dns;


import java.math.BigInteger;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.sync.Client;
import org.tron.p2p.dns.tree.Algorithm;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.dns.update.AwsClient;
import org.tron.p2p.dns.update.AwsClient.RecordSet;
import org.tron.p2p.exception.DnsException;
import org.tron.p2p.utils.ByteArray;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;

public class AwsRoute53Test {

  @Test
  public void testChangeSort() {

    Map<String, RecordSet> existing = new HashMap<>();
    existing.put("n", new RecordSet(new String[] {
        "enrtree-root:v1 e=2KFJOGVXDQTXXUGBH7GS7NAAAI l=FDXN3SN67NA5DKA4J2GOK7BVQI seq=0 sig=v_-J_q_9ICQg5ztExFvLQhDBGMb0lZPJLhe3ts9LAcgqhOhtT3YFJsl8BWNDSwGtamUdR-9xl88_w-X42SVpjwE"},
        AwsClient.rootTTL));
    existing.put("2kfjogvxdqtxxugbh7gs7naaai.n", new RecordSet(new String[] {
        "enr:-HW4QO1ml1DdXLeZLsUxewnthhUy8eROqkDyoMTyavfks9JlYQIlMFEUoM78PovJDPQrAkrb3LRJ-",
        "vtrymDguKCOIAWAgmlkgnY0iXNlY3AyNTZrMaEDffaGfJzgGhUif1JqFruZlYmA31HzathLSWxfbq_QoQ4"},
        3333));
    existing.put("fdxn3sn67na5dka4j2gok7bvqi.n",
        new RecordSet(new String[] {"enrtree-branch:"}, AwsClient.treeNodeTTL));

    Map<String, String> newRecords = new HashMap<>();
    newRecords.put("n",
        "enrtree-root:v1 e=JWXYDBPXYWG6FX3GMDIBFA6CJ4 l=C7HRFPF3BLGF3YR4DY5KX3SMBE seq=1 sig=o908WmNp7LibOfPsr4btQwatZJ5URBr2ZAuxvK4UWHlsB9sUOTJQaGAlLPVAhM__XJesCHxLISo94z5Z2a463gA");
    newRecords.put("c7hrfpf3blgf3yr4dy5kx3smbe.n",
        "enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@morenodes.example.org");
    newRecords.put("jwxydbpxywg6fx3gmdibfa6cj4.n",
        "enrtree-branch:2XS2367YHAXJFGLZHVAWLQD4ZY,H4FHT4B454P6UXFD7JCYQ5PWDY,MHTDO6TMUBRIA2XWG5LUDACK24");
    newRecords.put("2xs2367yhaxjfglzhvawlqd4zy.n",
        "enr:-HW4QOFzoVLaFJnNhbgMoDXPnOvcdVuj7pDpqRvh6BRDO68aVi5ZcjB3vzQRZH2IcLBGHzo8uUN3snqmgTiE56CH3AMBgmlkgnY0iXNlY3AyNTZrMaECC2_24YYkYHEgdzxlSNKQEnHhuNAbNlMlWJxrJxbAFvA");
    newRecords.put("h4fht4b454p6uxfd7jcyq5pwdy.n",
        "enr:-HW4QAggRauloj2SDLtIHN1XBkvhFZ1vtf1raYQp9TBW2RD5EEawDzbtSmlXUfnaHcvwOizhVYLtr7e6vw7NAf6mTuoCgmlkgnY0iXNlY3AyNTZrMaECjrXI8TLNXU0f8cthpAMxEshUyQlK-AM0PW2wfrnacNI");
    newRecords.put("mhtdo6tmubria2xwg5ludack24.n",
        "enr:-HW4QLAYqmrwllBEnzWWs7I5Ev2IAs7x_dZlbYdRdMUx5EyKHDXp7AV5CkuPGUPdvbv1_Ms1CPfhcGCvSElSosZmyoqAgmlkgnY0iXNlY3AyNTZrMaECriawHKWdDRk2xeZkrOXBQ0dfMFLHY4eENZwdufn1S1o");

    AwsClient publish = new AwsClient("random1", "random2", "random3",
        Region.US_EAST_1);
    List<Change> changes = publish.computeChanges("n", newRecords, existing);

    Change[] wantChanges = new Change[] {
        publish.newTXTChange(ChangeAction.CREATE, "2xs2367yhaxjfglzhvawlqd4zy.n",
            AwsClient.treeNodeTTL,
            "\"enr:-HW4QOFzoVLaFJnNhbgMoDXPnOvcdVuj7pDpqRvh6BRDO68aVi5ZcjB3vzQRZH2IcLBGHzo8uUN3snqmgTiE56CH3AMBgmlkgnY0iXNlY3AyNTZrMaECC2_24YYkYHEgdzxlSNKQEnHhuNAbNlMlWJxrJxbAFvA\""),
        publish.newTXTChange(ChangeAction.CREATE, "c7hrfpf3blgf3yr4dy5kx3smbe.n",
            AwsClient.treeNodeTTL,
            "\"enrtree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@morenodes.example.org\""),
        publish.newTXTChange(ChangeAction.CREATE, "h4fht4b454p6uxfd7jcyq5pwdy.n",
            AwsClient.treeNodeTTL,
            "\"enr:-HW4QAggRauloj2SDLtIHN1XBkvhFZ1vtf1raYQp9TBW2RD5EEawDzbtSmlXUfnaHcvwOizhVYLtr7e6vw7NAf6mTuoCgmlkgnY0iXNlY3AyNTZrMaECjrXI8TLNXU0f8cthpAMxEshUyQlK-AM0PW2wfrnacNI\""),
        publish.newTXTChange(ChangeAction.CREATE, "jwxydbpxywg6fx3gmdibfa6cj4.n",
            AwsClient.treeNodeTTL,
            "\"enrtree-branch:2XS2367YHAXJFGLZHVAWLQD4ZY,H4FHT4B454P6UXFD7JCYQ5PWDY,MHTDO6TMUBRIA2XWG5LUDACK24\""),
        publish.newTXTChange(ChangeAction.CREATE, "mhtdo6tmubria2xwg5ludack24.n",
            AwsClient.treeNodeTTL,
            "\"enr:-HW4QLAYqmrwllBEnzWWs7I5Ev2IAs7x_dZlbYdRdMUx5EyKHDXp7AV5CkuPGUPdvbv1_Ms1CPfhcGCvSElSosZmyoqAgmlkgnY0iXNlY3AyNTZrMaECriawHKWdDRk2xeZkrOXBQ0dfMFLHY4eENZwdufn1S1o\""),

        publish.newTXTChange(ChangeAction.UPSERT, "n",
            AwsClient.rootTTL,
            "\"enrtree-root:v1 e=JWXYDBPXYWG6FX3GMDIBFA6CJ4 l=C7HRFPF3BLGF3YR4DY5KX3SMBE seq=1 sig=o908WmNp7LibOfPsr4btQwatZJ5URBr2ZAuxvK4UWHlsB9sUOTJQaGAlLPVAhM__XJesCHxLISo94z5Z2a463gA\""),

        publish.newTXTChange(ChangeAction.DELETE, "2kfjogvxdqtxxugbh7gs7naaai.n",
            3333,
            "enr:-HW4QO1ml1DdXLeZLsUxewnthhUy8eROqkDyoMTyavfks9JlYQIlMFEUoM78PovJDPQrAkrb3LRJ-",
            "vtrymDguKCOIAWAgmlkgnY0iXNlY3AyNTZrMaEDffaGfJzgGhUif1JqFruZlYmA31HzathLSWxfbq_QoQ4"),
        publish.newTXTChange(ChangeAction.DELETE, "fdxn3sn67na5dka4j2gok7bvqi.n",
            AwsClient.treeNodeTTL,
            "enrtree-branch:")
    };

    Assert.assertEquals(wantChanges.length, changes.size());
    for (int i = 0; i < changes.size(); i++) {
      Assert.assertTrue(wantChanges[i].equalsBySdkFields(changes.get(i)));
      Assert.assertTrue(AwsClient.isSameChange(wantChanges[i], changes.get(i)));
    }
  }

  @Test
  public void testPublish() throws UnknownHostException {

    DnsNode[] nodes = TreeTest.sampleNode();
    List<DnsNode> nodeList = Arrays.asList(nodes);
    List<String> enrList = Tree.merge(nodeList);

    String[] links = new String[] {
        "enrtree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@example1.org",
        "enrtree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@example2.org"};
    List<String> linkList = Arrays.asList(links);

    Tree tree = new Tree();
    try {
      tree = tree.makeTree(1, enrList, linkList, AlgorithmTest.privateKey);
    } catch (DnsException e) {
      Assert.fail();
    }

    AwsClient awsClient = new AwsClient("replace your access key",
        "replace your access key secret",
        "replace your host zone id",
        Region.US_EAST_1);
    String domain = "replace with your domain";
    awsClient.deploy(domain, tree);

    BigInteger publicKeyInt = Algorithm.generateKeyPair(AlgorithmTest.privateKey).getPublicKey();
    String puKeyCompress = Algorithm.compressPubKey(publicKeyInt);
    String base32Pubkey = Algorithm.encode32(ByteArray.fromHexString(puKeyCompress));
    Client client = new Client();

    Tree route53Tree;
    try {
      route53Tree = client.syncTree(Entry.linkPrefix + base32Pubkey + "@" + domain);
    } catch (Exception e) {
      Assert.fail();
      return;
    }
    Assert.assertEquals(links.length, route53Tree.getLinksEntry().size());
    Assert.assertEquals(nodes.length, route53Tree.getNodes().size());
  }
}
