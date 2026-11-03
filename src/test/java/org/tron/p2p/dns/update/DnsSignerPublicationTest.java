package org.tron.p2p.dns.update;

import com.aliyun.alidns20150109.Client;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsRequest;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponse;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecords;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord;
import com.aliyun.teaopenapi.models.Config;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.Algorithm;
import org.tron.p2p.dns.tree.Entry;
import org.tron.p2p.dns.tree.RootEntry;
import org.tron.p2p.dns.tree.Tree;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsResponse;
import software.amazon.awssdk.services.route53.model.RRType;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;

@RunWith(Parameterized.class)
public class DnsSignerPublicationTest {

  private static final String DOMAIN = "nodes.example.invalid";
  private static final String OLD_KEY = "01";
  private static final String NEW_KEY = "02";
  private Fixture fixture;

  @Parameterized.Parameter
  public String provider;

  @Parameterized.Parameters(name = "{0}")
  public static Collection<Object[]> providers() {
    return Arrays.asList(new Object[][]{{"Aliyun"}, {"Route53"}});
  }

  @Before
  public void setUp() throws Exception {
    fixture = provider.equals("Aliyun") ? new AliFixture() : new AwsFixture();
  }

  @Test
  public void signerChangePublishesWithUnchangedNodesAndLinks() throws Exception {
    Tree published = tree(OLD_KEY, "192.0.2.1");
    fixture.load(published);
    Tree updated = tree(NEW_KEY, "192.0.2.1");

    fixture.deploy(updated);

    Assert.assertEquals(published.getRootEntry().getERoot(), updated.getRootEntry().getERoot());
    Assert.assertEquals(published.getRootEntry().getLRoot(), updated.getRootEntry().getLRoot());
    Assert.assertEquals(Collections.singletonList("@"), fixture.writes);
    Assert.assertEquals(8, fixture.root().getSeq());
    assertSigner(NEW_KEY, true);
    assertSigner(OLD_KEY, false);
  }

  @Test
  public void sameSignerWithNewSequenceAndSignatureDoesNotPublish() throws Exception {
    Tree published = tree(OLD_KEY, "192.0.2.1");
    fixture.load(published);
    Tree updated = tree(OLD_KEY, "192.0.2.1");

    fixture.deploy(updated);

    Assert.assertEquals(8, updated.getSeq());
    Assert.assertNotEquals(published.getRootEntry().toFormat(),
        updated.getRootEntry().toFormat());
    Assert.assertTrue(fixture.writes.isEmpty());
    Assert.assertEquals(published.getRootEntry().toFormat(), fixture.root().toFormat());
  }

  @Test
  public void sameSignerWithLeadingZeroPublicKeyDoesNotPublish() throws Exception {
    // Public-key identity must not depend on the compressed URL encoding.
    fixture.load(tree("2d", "192.0.2.1"));

    fixture.deploy(tree("2d", "192.0.2.1"));

    Assert.assertTrue(fixture.writes.isEmpty());
    assertSigner("2d", true);
  }

  @Test
  public void sameSignerKeepsNodeChangesBelowThresholdUnpublished() throws Exception {
    String[] ips = addresses(12);
    fixture.load(tree(OLD_KEY, Arrays.copyOf(ips, 11)));

    fixture.deploy(tree(OLD_KEY, ips));

    Assert.assertTrue(fixture.writes.isEmpty());
    Assert.assertEquals(7, fixture.root().getSeq());
  }

  @Test
  public void signerChangePublishesNodeChangesBelowThreshold() throws Exception {
    String[] ips = addresses(12);
    fixture.load(tree(OLD_KEY, Arrays.copyOf(ips, 11)));
    Tree updated = tree(NEW_KEY, ips);

    fixture.deploy(updated);

    Assert.assertEquals(updated.getRootEntry().toFormat(), fixture.root().toFormat());
    assertSigner(NEW_KEY, true);
  }

  @Test
  public void sameSignerStillPublishesNodeChangesAtThreshold() throws Exception {
    String[] ips = addresses(11);
    fixture.load(tree(OLD_KEY, Arrays.copyOf(ips, 10)));
    Tree updated = tree(OLD_KEY, ips);

    fixture.deploy(updated);

    Assert.assertEquals(updated.getRootEntry().toFormat(), fixture.root().toFormat());
    Assert.assertTrue(fixture.writes.contains("@"));
    assertSigner(OLD_KEY, true);
  }

  @Test
  public void successfulKeyChangeDoesNotRepublishOnNextDeployment() throws Exception {
    fixture.load(tree(OLD_KEY, "192.0.2.1"));
    fixture.deploy(tree(NEW_KEY, "192.0.2.1"));
    Assert.assertEquals(8, fixture.root().getSeq());
    fixture.writes.clear();

    fixture.deploy(tree(NEW_KEY, "192.0.2.1"));

    Assert.assertTrue(fixture.writes.isEmpty());
    Assert.assertEquals(8, fixture.root().getSeq());
    assertSigner(NEW_KEY, true);
  }

  @Test
  public void failedKeyChangeCanRetryThePublishedSigner() throws Exception {
    Tree published = tree(OLD_KEY, "192.0.2.1");
    fixture.load(published);
    fixture.failRootWrite = true;

    Assert.assertThrows(Exception.class, () -> fixture.deploy(tree(NEW_KEY, "192.0.2.1")));

    Assert.assertEquals(published.getRootEntry().toFormat(), fixture.root().toFormat());
    fixture.failRootWrite = false;
    fixture.deploy(tree(NEW_KEY, "192.0.2.1"));

    Assert.assertEquals(8, fixture.root().getSeq());
    assertSigner(NEW_KEY, true);
  }

  @Test
  public void missingRootDoesNotReusePreviouslyCollectedSigner() throws Exception {
    fixture.load(tree(OLD_KEY, "192.0.2.1"));
    fixture.collect();
    fixture.records.remove("@");

    fixture.deploy(tree(OLD_KEY, "192.0.2.1"));

    Assert.assertEquals(Collections.singletonList("@"), fixture.writes);
    assertSigner(OLD_KEY, true);
  }

  private void assertSigner(String privateKey, boolean expected) throws Exception {
    RootEntry root = fixture.root();
    String publicKey = Algorithm.generateKeyPair(privateKey).getPublicKey().toString(16);
    Assert.assertEquals(expected,
        Algorithm.verifySignature(publicKey, root.toString(), root.getSignature()));
  }

  private static Tree tree(String privateKey, String... ips) throws Exception {
    List<String> nodes = new ArrayList<>();
    for (String ip : ips) {
      nodes.add(Entry.nodesPrefix + DnsNode.compress(Collections.singletonList(
          new DnsNode(null, ip, null, 18888))));
    }
    Tree tree = new Tree();
    tree.makeTree(7, nodes, Collections.emptyList(), privateKey);
    return tree;
  }

  private static String[] addresses(int size) {
    String[] ips = new String[size];
    for (int i = 0; i < size; i++) {
      ips[i] = "192.0.2." + (i + 1);
    }
    return ips;
  }

  private static void replaceField(Object target, Class<?> owner, String name, Object value)
      throws Exception {
    Field field = owner.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private abstract static class Fixture {
    final Map<String, String> records = new HashMap<>();
    final List<String> writes = new ArrayList<>();
    boolean failRootWrite;

    void load(Tree tree) {
      records.clear();
      records.putAll(tree.toTXT(null));
    }

    RootEntry root() throws Exception {
      return RootEntry.parseEntry(records.get("@"));
    }

    void write(String name, String value) {
      if (name.equals("@") && failRootWrite) {
        throw new IllegalStateException("Injected root write failure");
      }
      writes.add(name);
      records.put(name, value);
    }

    abstract void deploy(Tree tree) throws Exception;

    abstract void collect() throws Exception;
  }

  private static class AliFixture extends Fixture {
    private final AliClient client;

    AliFixture() throws Exception {
      client = new AliClient("alidns.aliyuncs.com", "test-id", "test-secret", 0.1) {
        @Override
        public boolean addRecord(String domain, String name, String value, long ttl) {
          write(name, value);
          return true;
        }

        @Override
        public boolean updateRecord(String id, String name, String value, long ttl) {
          write(name, value);
          return true;
        }

        @Override
        public boolean deleteRecord(String id) {
          writes.add(id);
          records.remove(id);
          return true;
        }
      };
      Client sdkClient = new Client(new Config().setAccessKeyId("test-id")
          .setAccessKeySecret("test-secret").setEndpoint("alidns.aliyuncs.com")) {
        @Override
        public DescribeDomainRecordsResponse describeDomainRecords(
            DescribeDomainRecordsRequest request) {
          List<DescribeDomainRecordsResponseBodyDomainRecordsRecord> responseRecords =
              new ArrayList<>();
          records.forEach((name, value) -> responseRecords.add(
              new DescribeDomainRecordsResponseBodyDomainRecordsRecord().setRR(name)
                  .setRecordId(name).setValue(value)
                  .setTTL(name.equals("@") ? (long) Publish.rootTTL : 24L * 60 * 60)));
          return new DescribeDomainRecordsResponse().setStatusCode(200)
              .setBody(new DescribeDomainRecordsResponseBody()
                  .setTotalCount((long) responseRecords.size())
                  .setDomainRecords(new DescribeDomainRecordsResponseBodyDomainRecords()
                      .setRecord(responseRecords)));
        }
      };
      replaceField(client, AliClient.class, "aliDnsClient", sdkClient);
    }

    @Override
    void deploy(Tree tree) throws Exception {
      client.deploy(DOMAIN, tree);
    }

    @Override
    void collect() throws Exception {
      client.collectRecords(DOMAIN);
    }
  }

  private static class AwsFixture extends Fixture {
    private final AwsClient client;

    AwsFixture() throws Exception {
      client = new AwsClient("test-id", "test-secret", "ZTEST", "us-east-1", 0.1) {
        @Override
        public void submitChanges(List<Change> changes, String comment) {
          for (Change change : changes) {
            ResourceRecordSet record = change.resourceRecordSet();
            String name = record.name().equals(DOMAIN) ? "@"
                : record.name().substring(0, record.name().length() - DOMAIN.length() - 1);
            if (change.action() == ChangeAction.DELETE) {
              writes.add(name);
              records.remove(name);
            } else {
              StringBuilder value = new StringBuilder();
              record.resourceRecords().forEach(part ->
                  value.append(part.value().replace("\"", "")));
              write(name, value.toString());
            }
          }
        }
      };
      Field sdkField = AwsClient.class.getDeclaredField("route53Client");
      sdkField.setAccessible(true);
      ((Route53Client) sdkField.get(client)).close();
      Route53Client sdkClient = (Route53Client) Proxy.newProxyInstance(
          Route53Client.class.getClassLoader(), new Class<?>[]{Route53Client.class},
          (proxy, method, args) -> {
            if (!method.getName().equals("listResourceRecordSets")) {
              throw new AssertionError("Unexpected Route53 call: " + method.getName());
            }
            List<ResourceRecordSet> responseRecords = new ArrayList<>();
            records.forEach((name, value) -> responseRecords.add(ResourceRecordSet.builder()
                .name(name.equals("@") ? DOMAIN : name + "." + DOMAIN).type(RRType.TXT)
                .ttl(name.equals("@") ? (long) Publish.rootTTL : Publish.treeNodeTTL)
                .resourceRecords(ResourceRecord.builder().value("\"" + value + "\"").build())
                .build()));
            return ListResourceRecordSetsResponse.builder().isTruncated(false)
                .resourceRecordSets(responseRecords).build();
          });
      replaceField(client, AwsClient.class, "route53Client", sdkClient);
    }

    @Override
    void deploy(Tree tree) throws Exception {
      client.deploy(DOMAIN, tree);
    }

    @Override
    void collect() throws Exception {
      client.collectRecords(DOMAIN);
    }
  }
}
