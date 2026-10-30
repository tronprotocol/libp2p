package org.tron.p2p.dns.update;


import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.Tree;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameResponse;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsResponse;
import software.amazon.awssdk.services.route53.model.RRType;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;

public class AwsClientDomainCaseTest {

  private static final String DOMAIN = "nodes.example.invalid";
  private static final String MIXED_DOMAIN = "Nodes.Example.INVALID.";
  private static final String ZONE_ID = "ZTEST";

  @Test
  public void subdomainComparisonIgnoresCaseButKeepsLabelBoundaries() {
    Assert.assertTrue(AwsClient.isSubdomain("Leaf.Nodes.Example.INVALID.", DOMAIN));
    Assert.assertTrue(AwsClient.isSubdomain(DOMAIN, "EXAMPLE.INVALID."));
    Assert.assertTrue(AwsClient.isSubdomain(DOMAIN, MIXED_DOMAIN));
    Assert.assertFalse(AwsClient.isSubdomain("othernodes.example.invalid", DOMAIN));
    Assert.assertFalse(AwsClient.isSubdomain("nodes.example.invalid.other", DOMAIN));
  }

  @Test
  public void treeRecordNamesAreCanonicalRegardlessOfDefaultLocale() throws Exception {
    Locale previous = Locale.getDefault();
    try {
      Locale.setDefault(new Locale("tr", "TR"));
      Tree tree = tree("192.0.2.1");

      Map<String, String> records = tree.toTXT(MIXED_DOMAIN);

      Assert.assertEquals(tree.getRootEntry().toFormat(), records.get(DOMAIN));
      for (Map.Entry<String, Entry> entry : tree.getEntries().entrySet()) {
        String name = entry.getKey().toLowerCase(Locale.ROOT) + "." + DOMAIN;
        Assert.assertEquals(entry.getValue().toString(), records.get(name));
      }
    } finally {
      Locale.setDefault(previous);
    }
  }

  @Test
  public void collectedRecordKeysMatchCanonicalTreeNames() throws Exception {
    RecordingAwsClient client = new RecordingAwsClient(ZONE_ID);
    Tree tree = tree("192.0.2.1");
    for (Map.Entry<String, String> entry : tree.toTXT(DOMAIN).entrySet()) {
      client.api.put(txt(entry.getKey().toUpperCase(Locale.ROOT) + ".", entry.getValue()));
    }

    Map<String, AwsClient.RecordSet> existing = client.collectRecords(MIXED_DOMAIN);

    Assert.assertEquals(tree.toTXT(DOMAIN).keySet(), existing.keySet());
    Assert.assertTrue(client.computeChanges(MIXED_DOMAIN, tree.toTXT(MIXED_DOMAIN), existing)
        .isEmpty());
  }

  @Test
  public void mixedCaseDomainCanPublishTwiceWithoutDuplicateCreates() throws Exception {
    RecordingAwsClient client = new RecordingAwsClient(ZONE_ID);
    client.deploy(MIXED_DOMAIN, tree("192.0.2.1"));
    Tree updated = tree("192.0.2.2");

    client.deploy(MIXED_DOMAIN, updated);

    Assert.assertEquals(2, updated.getSeq());
    Assert.assertTrue(client.changes.stream().anyMatch(change ->
        change.action() == ChangeAction.UPSERT
            && change.resourceRecordSet().name().equals(DOMAIN)));
    Assert.assertTrue(client.changes.stream().anyMatch(change ->
        change.action() == ChangeAction.DELETE));
    Assert.assertEquals(updated.toTXT(DOMAIN).keySet(), client.api.records.keySet());
  }

  @Test
  public void hostedZoneLookupIgnoresCaseAndTrailingDot() throws Exception {
    RecordingAwsClient client = new RecordingAwsClient(null);

    client.deploy(MIXED_DOMAIN, tree("192.0.2.1"));

    Assert.assertEquals(1, client.api.zoneQueries);
    Assert.assertEquals(ZONE_ID, client.api.requestedZone);
    Assert.assertTrue(client.api.records.containsKey(DOMAIN));
  }

  @Test
  public void deleteDomainFindsMixedCaseRecordsWithoutMatchingOtherDomains() throws Exception {
    RecordingAwsClient client = new RecordingAwsClient(ZONE_ID);
    client.deploy(DOMAIN, tree("192.0.2.1"));
    client.api.put(txt("other.invalid.", "unrelated TXT"));

    Assert.assertTrue(client.deleteDomain(MIXED_DOMAIN));

    Assert.assertEquals(Collections.singleton("other.invalid"), client.api.records.keySet());
    Assert.assertTrue(client.changes.stream().allMatch(change ->
        change.action() == ChangeAction.DELETE));
  }

  private static Tree tree(String ip) throws Exception {
    String nodes = Entry.nodesPrefix + DnsNode.compress(Collections.singletonList(
        new DnsNode(null, ip, null, 18888)));
    Tree tree = new Tree();
    tree.makeTree(0, Collections.singletonList(nodes), Collections.emptyList(), "01");
    return tree;
  }

  private static ResourceRecordSet txt(String name, String value) {
    return ResourceRecordSet.builder().name(name).type(RRType.TXT)
        .ttl((long) (nameKey(name).equals(DOMAIN) ? Publish.rootTTL : Publish.treeNodeTTL))
        .resourceRecords(ResourceRecord.builder().value("\"" + value + "\"").build()).build();
  }

  private static String nameKey(String name) {
    return StringUtils.stripEnd(name, ".").toLowerCase(Locale.ROOT);
  }

  private static class RecordingAwsClient extends AwsClient {
    private final MemoryRoute53 api = new MemoryRoute53();
    private List<Change> changes = new ArrayList<>();

    private RecordingAwsClient(String zoneId) throws Exception {
      super("test-key", "test-secret", zoneId, "us-east-1", 0.1);
      Field field = AwsClient.class.getDeclaredField("route53Client");
      field.setAccessible(true);
      ((Route53Client) field.get(this)).close();
      field.set(this, Proxy.newProxyInstance(Route53Client.class.getClassLoader(),
          new Class<?>[]{Route53Client.class}, api));
    }

    @Override
    public void submitChanges(List<Change> changes, String comment) {
      this.changes = new ArrayList<>(changes);
      api.apply(changes);
    }
  }

  private static class MemoryRoute53 implements InvocationHandler {
    private final Map<String, ResourceRecordSet> records = new LinkedHashMap<>();
    private int zoneQueries;
    private String requestedZone;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
      switch (method.getName()) {
        case "listHostedZonesByName":
          zoneQueries++;
          return ListHostedZonesByNameResponse.builder().isTruncated(false)
              .hostedZones(HostedZone.builder().id("/hostedzone/" + ZONE_ID)
                  .name("EXAMPLE.INVALID.").build()).build();
        case "listResourceRecordSets":
          requestedZone = ((ListResourceRecordSetsRequest) args[0]).hostedZoneId();
          Assert.assertEquals(ZONE_ID, requestedZone);
          return ListResourceRecordSetsResponse.builder().isTruncated(false)
              .resourceRecordSets(records.values()).build();
        case "close":
          return null;
        default:
          throw new AssertionError("Unexpected Route53 call: " + method.getName());
      }
    }

    private void put(ResourceRecordSet record) {
      records.put(nameKey(record.name()), record);
    }

    private void apply(List<Change> changes) {
      for (Change change : changes) {
        ResourceRecordSet record = change.resourceRecordSet();
        String key = nameKey(record.name());
        if (change.action() == ChangeAction.DELETE) {
          Assert.assertNotNull("Deleting a missing record: " + key, records.remove(key));
        } else {
          if (change.action() == ChangeAction.CREATE) {
            Assert.assertFalse("Creating an existing record: " + key, records.containsKey(key));
          }
          put(record.toBuilder().name(key + ".").build());
        }
      }
    }
  }
}
