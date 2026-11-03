package org.tron.p2p.dns.update;


import com.aliyun.alidns20150109.Client;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponse;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecords;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord;
import com.aliyun.teaopenapi.models.Config;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.TreeTest;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;

public class AliClientPublishOrderTest {

  private static final String DOMAIN = "nodes.example.invalid";

  @Test
  public void publishesDependenciesThenRootThenDeletesOldRecords() throws Exception {
    for (boolean update : new boolean[]{false, true}) {
      RecordingClient client = new RecordingClient();
      Tree tree = tree();
      String dependency = dependency(tree);
      if (update) {
        client.existing.put("@", record("@", "old root"));
        client.existing.put(dependency, record(dependency, tree.toTXT(null).get(dependency)));
        client.existing.put("stale", record("stale", "old node"));
      }

      client.deploy(DOMAIN, tree);

      assertRootLast(client, tree, update ? "update:@" : "add:@");
      Assert.assertEquals(tree.toTXT(null).size() + (update ? 1 : 0), client.calls.size());
      if (update) {
        Assert.assertTrue(client.calls.contains("update:" + dependency));
        Assert.assertEquals("delete:stale", client.calls.get(client.calls.size() - 1));
      }
    }
  }

  @Test
  public void failedWritesDoNotAdvancePublicationOrDeleteOldRecords() throws Exception {
    for (boolean rootFailure : new boolean[]{false, true}) {
      for (boolean update : new boolean[]{false, true}) {
        for (boolean throwFailure : new boolean[]{false, true}) {
          RecordingClient client = new RecordingClient();
          Tree tree = tree();
          client.failedName = rootFailure ? "@" : dependency(tree);
          client.throwFailure = throwFailure;
          client.existing.put("stale", record("stale", "old node"));
          if (update) {
            client.existing.put("@", record("@", "old root"));
            client.existing.put(client.failedName, record(client.failedName, "old record"));
          }

          DnsException error = Assert.assertThrows(DnsException.class,
              () -> client.deploy(DOMAIN, tree));

          Assert.assertEquals(DnsException.TypeEnum.DEPLOY_DOMAIN_FAILED, error.getType());
          Assert.assertTrue(client.calls.contains((update ? "update:" : "add:")
              + client.failedName));
          Assert.assertFalse(client.calls.contains("delete:stale"));
          if (rootFailure) {
            assertRootLast(client, tree, update ? "update:@" : "add:@");
            Assert.assertEquals(tree.toTXT(null).size(), client.calls.size());
          } else {
            Assert.assertFalse(client.calls.contains("add:@"));
            Assert.assertFalse(client.calls.contains("update:@"));
          }
        }
      }
    }
  }

  @Test
  public void linkOnlyChangesTriggerPublication() throws Exception {
    for (boolean addLink : new boolean[]{false, true}) {
      RecordingClient client = new RecordingClient(true);
      Tree withLinks = TreeTest.signedTree(new Tree(), "192.0.2.1");
      Tree withoutLinks = withoutLinks(withLinks);
      Tree published = addLink ? withoutLinks : withLinks;
      Tree updated = addLink ? withLinks : withoutLinks;
      client.loadTree(published);

      client.deploy(DOMAIN, updated);

      Assert.assertEquals(published.getDnsNodes(), updated.getDnsNodes());
      Assert.assertNotEquals(published.getRootEntry().getLRoot(),
          updated.getRootEntry().getLRoot());
      Assert.assertTrue(client.calls.contains("update:@"));
    }
  }

  @Test
  public void nodeChangesBelowThresholdDoNotPublish() throws Exception {
    RecordingClient client = new RecordingClient(true);
    String[] ips = new String[12];
    for (int i = 0; i < ips.length; i++) {
      ips[i] = "192.0.2." + (i + 1);
    }
    Tree published = TreeTest.signedTree(new Tree(), Arrays.copyOf(ips, 11));
    Tree updated = TreeTest.signedTree(new Tree(), ips);
    client.loadTree(published);

    client.deploy(DOMAIN, updated);

    Assert.assertNotEquals(published.getRootEntry().getERoot(),
        updated.getRootEntry().getERoot());
    Assert.assertEquals(published.getRootEntry().getLRoot(),
        updated.getRootEntry().getLRoot());
    Assert.assertTrue(client.calls.isEmpty());
  }

  @Test
  public void unchangedTreeWithNewSequenceDoesNotPublish() throws Exception {
    RecordingClient client = new RecordingClient(true);
    Tree published = TreeTest.signedTree(new Tree(), "192.0.2.1");
    published.setSeq(7);
    published.sign();
    client.loadTree(published);
    Tree updated = TreeTest.signedTree(new Tree(), "192.0.2.1");

    client.deploy(DOMAIN, updated);

    Assert.assertEquals(8, updated.getSeq());
    Assert.assertNotEquals(published.getRootEntry().toFormat(),
        updated.getRootEntry().toFormat());
    Assert.assertTrue(client.calls.isEmpty());
  }

  @Test
  public void missingRootDoesNotReusePreviouslyCollectedLinkRoot() throws Exception {
    RecordingClient client = new RecordingClient(true);
    Tree published = TreeTest.signedTree(new Tree(), "192.0.2.1");
    client.loadTree(published);
    client.collectRecords(DOMAIN);
    client.existing.remove(AliClient.aliyunRoot);

    client.deploy(DOMAIN, TreeTest.signedTree(new Tree(), "192.0.2.1"));

    Assert.assertTrue(client.calls.contains("add:@"));
  }

  private static Tree withoutLinks(Tree tree) throws Exception {
    Tree result = new Tree();
    result.makeTree(0, tree.getNodesEntry(), Collections.emptyList(), "01");
    return result;
  }

  private static Tree tree() throws Exception {
    return TreeTest.signedTree(new Tree() {
      @Override
      public Map<String, String> toTXT(String rootDomain) {
        Map<String, String> records = super.toTXT(rootDomain);
        Map<String, String> rootFirst = new LinkedHashMap<>();
        rootFirst.put("@", records.get("@"));
        rootFirst.putAll(records);
        return rootFirst;
      }
    }, "192.0.2.1", "192.0.2.2");
  }

  private static String dependency(Tree tree) {
    return tree.toTXT(null).keySet().stream().filter(name -> !name.equals("@"))
        .findFirst().get();
  }

  private static DescribeDomainRecordsResponseBodyDomainRecordsRecord record(
      String name, String value) {
    return new DescribeDomainRecordsResponseBodyDomainRecordsRecord()
        .setRR(name).setRecordId(name).setValue(value).setTTL(0L);
  }

  private static void assertRootLast(RecordingClient client, Tree tree, String rootCall) {
    Assert.assertEquals(tree.toTXT(null).size() - 1, client.calls.indexOf(rootCall));
    for (String name : tree.toTXT(null).keySet()) {
      Assert.assertTrue(client.calls.contains("add:" + name)
          || client.calls.contains("update:" + name));
    }
  }

  private static class RecordingClient extends AliClient {
    private final Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> existing =
        new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private String failedName;
    private boolean throwFailure;
    private final boolean collectCloudRecords;

    private RecordingClient() throws Exception {
      this(false);
    }

    private RecordingClient(boolean collectCloudRecords) throws Exception {
      super("alidns.aliyuncs.com", "test-id", "test-secret", 0.1);
      this.collectCloudRecords = collectCloudRecords;
      if (collectCloudRecords) {
        Client sdkClient = new Client(new Config().setAccessKeyId("test-id")
            .setAccessKeySecret("test-secret").setEndpoint("alidns.aliyuncs.com")) {
          @Override
          public DescribeDomainRecordsResponse describeDomainRecords(
              DescribeDomainRecordsRequest request) {
            List<DescribeDomainRecordsResponseBodyDomainRecordsRecord> records =
                new ArrayList<>(existing.values());
            return new DescribeDomainRecordsResponse().setStatusCode(200)
                .setBody(new DescribeDomainRecordsResponseBody()
                    .setTotalCount((long) records.size())
                    .setDomainRecords(new DescribeDomainRecordsResponseBodyDomainRecords()
                        .setRecord(records)));
          }
        };
        Field field = AliClient.class.getDeclaredField("aliDnsClient");
        field.setAccessible(true);
        field.set(this, sdkClient);
      }
    }

    private void loadTree(Tree tree) {
      tree.toTXT(null).forEach((name, value) -> existing.put(name,
          record(name, value).setTTL(name.equals(AliClient.aliyunRoot)
              ? (long) Publish.rootTTL : 24L * 60 * 60)));
    }

    @Override
    public Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> collectRecords(
        String domain) throws Exception {
      return collectCloudRecords ? super.collectRecords(domain) : existing;
    }

    @Override
    public boolean addRecord(String domain, String name, String value, long ttl) throws Exception {
      return write("add:", name);
    }

    @Override
    public boolean updateRecord(String id, String name, String value, long ttl) throws Exception {
      return write("update:", name);
    }

    @Override
    public boolean deleteRecord(String id) {
      calls.add("delete:" + id);
      return true;
    }

    private boolean write(String action, String name) throws IOException {
      calls.add(action + name);
      if (name.equals(failedName)) {
        if (throwFailure) {
          throw new IOException("Injected write failure");
        }
        return false;
      }
      return true;
    }
  }
}
