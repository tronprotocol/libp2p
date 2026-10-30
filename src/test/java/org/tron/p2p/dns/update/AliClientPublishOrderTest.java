package org.tron.p2p.dns.update;


import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;

public class AliClientPublishOrderTest {

  private static final String DOMAIN = "nodes.example.invalid";

  @Test
  public void firstPublishAddsEveryDependencyBeforeTheRoot() throws Exception {
    RecordingClient client = new RecordingClient();
    Tree tree = tree();

    client.deploy(DOMAIN, tree);

    assertRootLast(client, tree, "add:@");
    Assert.assertEquals(tree.toTXT(null).size(), client.calls.size());
  }

  @Test
  public void updatePublishesDependenciesThenRootThenDeletesOldRecords() throws Exception {
    RecordingClient client = new RecordingClient();
    Tree tree = tree();
    String dependency = dependency(tree);
    client.existing.put("@", record("@", "old root"));
    client.existing.put(dependency, record(dependency, tree.toTXT(null).get(dependency)));
    client.existing.put("stale", record("stale", "old node"));

    client.deploy(DOMAIN, tree);

    assertRootLast(client, tree, "update:@");
    Assert.assertTrue(client.calls.contains("update:" + dependency));
    Assert.assertEquals("delete:stale", client.calls.get(client.calls.size() - 1));
  }

  @Test
  public void dependencyFailureDoesNotPublishRootOrDeleteOldRecords() throws Exception {
    for (boolean update : new boolean[]{false, true}) {
      for (boolean throwFailure : new boolean[]{false, true}) {
        RecordingClient client = new RecordingClient();
        Tree tree = tree();
        client.failedName = dependency(tree);
        client.throwFailure = throwFailure;
        client.existing.put("@", record("@", "old root"));
        client.existing.put("stale", record("stale", "old node"));
        if (update) {
          client.existing.put(client.failedName, record(client.failedName, "old dependency"));
        }

        assertPublishFailed(client, tree);

        Assert.assertTrue(client.calls.contains((update ? "update:" : "add:")
            + client.failedName));
        Assert.assertFalse(client.calls.contains("add:@"));
        Assert.assertFalse(client.calls.contains("update:@"));
        Assert.assertFalse(client.calls.contains("delete:stale"));
      }
    }
  }

  @Test
  public void rootFailureLeavesOldRecordsAfterWritingDependencies() throws Exception {
    for (boolean update : new boolean[]{false, true}) {
      for (boolean throwFailure : new boolean[]{false, true}) {
        RecordingClient client = new RecordingClient();
        Tree tree = tree();
        client.failedName = "@";
        client.throwFailure = throwFailure;
        client.existing.put("stale", record("stale", "old node"));
        if (update) {
          client.existing.put("@", record("@", "old root"));
        }

        assertPublishFailed(client, tree);

        assertRootLast(client, tree, update ? "update:@" : "add:@");
        Assert.assertEquals(tree.toTXT(null).size(), client.calls.size());
      }
    }
  }

  private static Tree tree() throws Exception {
    Tree tree = new Tree() {
      @Override
      public Map<String, String> toTXT(String rootDomain) {
        Map<String, String> records = super.toTXT(rootDomain);
        Map<String, String> rootFirst = new LinkedHashMap<>();
        rootFirst.put("@", records.get("@"));
        rootFirst.putAll(records);
        return rootFirst;
      }
    };
    List<String> nodes = new ArrayList<>();
    for (String ip : Arrays.asList("192.0.2.1", "192.0.2.2")) {
      nodes.add(Entry.nodesPrefix + DnsNode.compress(Collections.singletonList(
          new DnsNode(null, ip, null, 18888))));
    }
    tree.makeTree(0, nodes, Collections.singletonList(
        "tree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@linked.example.invalid"),
        "01");
    return tree;
  }

  private static String dependency(Tree tree) {
    return tree.toTXT(null).keySet().stream().filter(name -> !name.equals("@"))
        .findFirst().get();
  }

  private static DescribeDomainRecordsResponseBodyDomainRecordsRecord record(
      String name, String value) {
    return new DescribeDomainRecordsResponseBodyDomainRecordsRecord()
        .setRecordId(name).setValue(value).setTTL(0L);
  }

  private static void assertRootLast(RecordingClient client, Tree tree, String rootCall) {
    List<String> writes = new ArrayList<>(client.calls);
    writes.removeIf(call -> call.startsWith("delete:"));
    Assert.assertEquals(tree.toTXT(null).size(), writes.size());
    Assert.assertEquals(rootCall, writes.get(writes.size() - 1));
    for (String name : tree.toTXT(null).keySet()) {
      Assert.assertTrue(writes.contains("add:" + name) || writes.contains("update:" + name));
    }
  }

  private static void assertPublishFailed(RecordingClient client, Tree tree) {
    try {
      client.deploy(DOMAIN, tree);
      Assert.fail("Expected publishing to stop on a failed write");
    } catch (DnsException expected) {
      Assert.assertEquals(DnsException.TypeEnum.DEPLOY_DOMAIN_FAILED, expected.getType());
    }
  }

  private static class RecordingClient extends AliClient {
    private final Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> existing =
        new HashMap<>();
    private final List<String> calls = new ArrayList<>();
    private String failedName;
    private boolean throwFailure;

    private RecordingClient() throws Exception {
      super("alidns.aliyuncs.com", "test-id", "test-secret", 0.1);
    }

    @Override
    public Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> collectRecords(
        String domain) {
      return existing;
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
