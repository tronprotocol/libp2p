package org.tron.p2p.dns.update;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.dns.tree.RootEntry;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.ChangeBatch;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsResponse;
import software.amazon.awssdk.services.route53.model.ChangeStatus;
import software.amazon.awssdk.services.route53.model.GetChangeRequest;
import software.amazon.awssdk.services.route53.model.GetChangeResponse;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameRequest;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameResponse;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsResponse;
import software.amazon.awssdk.services.route53.model.RRType;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;

@Slf4j(topic = "net")
public class AwsClient implements Publish {

  public static final int route53ChangeSizeLimit = 32000;
  public static final int route53ChangeCountLimit = 1000;
  public static final int maxRetryLimit = 60;
  private int lastSeq = 0;
  private Route53Client route53Client;
  private String zoneId;

  public AwsClient(final String accessKey, final String accessKeySecret,
      final String zoneId, final Region region) {
    if (StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(accessKeySecret)) {
      throw new RuntimeException("Need Route53 Access Key ID and secret to proceed");
    }
    StaticCredentialsProvider staticCredentialsProvider = StaticCredentialsProvider.create(
        new AwsCredentials() {
          @Override
          public String accessKeyId() {
            return accessKey;
          }

          @Override
          public String secretAccessKey() {
            return accessKeySecret;
          }
        });
    route53Client = Route53Client.builder()
        .credentialsProvider(staticCredentialsProvider)
        .region(region)
        .build();
    this.zoneId = zoneId;
  }

  private void checkZone(String domain) {
    if (StringUtils.isEmpty(this.zoneId)) {
      this.zoneId = findZoneID(domain);
    }
  }

  private String findZoneID(String domain) {
    log.info("Finding Route53 Zone ID for {}", domain);
    ListHostedZonesByNameRequest.Builder request = ListHostedZonesByNameRequest.builder();
    while (true) {
      ListHostedZonesByNameResponse response = route53Client.listHostedZonesByName(request.build());
      for (HostedZone hostedZone : response.hostedZones()) {
        if (isSubdomain(domain, hostedZone.name())) {
          // example: /hostedzone/Z0404776204LVYA8EZNVH
          return hostedZone.id().split("/")[2];
        }
      }
      if (!response.isTruncated()) {
        break;
      }
      request.dnsName(response.dnsName());
      request.hostedZoneId(response.nextHostedZoneId());
    }
    return null;
  }

  // uploads the given tree to Route53.
  @Override
  public void deploy(String domain, Tree tree) throws DnsException {
    checkZone(domain);

    Map<String, RecordSet> existing = collectRecords(domain);
    log.info("Find {} TXT records for {}", existing.size(), domain);

    tree.setSeq(this.lastSeq + 1);
    Map<String, String> records = tree.toTXT(domain);

    List<Change> changes = computeChanges(domain, records, existing);

    String comment = String.format("Enrtree update of %s at seq %d", domain, tree.getSeq());
    submitChanges(changes, comment);
  }

  // removes all TXT records of the given domain.
  @Override
  public boolean deleteDomain(String rootDomain) throws DnsException {
    checkZone(rootDomain);

    Map<String, RecordSet> existing = collectRecords(rootDomain);
    log.info("Find {} TXT records for {}", existing.size(), rootDomain);

    List<Change> changes = makeDeletionChanges(new HashMap<>(), existing);

    String comment = String.format("delete entree of %s", rootDomain);
    submitChanges(changes, comment);
    return true;
  }

  // collects all TXT records below the given name. it also update lastSeq
  @Override
  public Map<String, RecordSet> collectRecords(String rootDomain) throws DnsException {
    Map<String, RecordSet> existing = new HashMap<>();
    ListResourceRecordSetsRequest.Builder request = ListResourceRecordSetsRequest.builder();
    request.hostedZoneId(zoneId);
    int page = 0;

    String rootContent = null;
    while (true) {
      log.info("Loading existing TXT records from name:{} zoneId:{} page:{}", rootDomain, zoneId,
          page);
      ListResourceRecordSetsResponse response = route53Client.listResourceRecordSets(
          request.build());

      List<ResourceRecordSet> recordSetList = response.resourceRecordSets();
      for (ResourceRecordSet resourceRecordSet : recordSetList) {
        if (!isSubdomain(resourceRecordSet.name(), rootDomain)
            || resourceRecordSet.type() != RRType.TXT) {
          continue;
        }
        List<String> values = new ArrayList<>();
        for (ResourceRecord resourceRecord : resourceRecordSet.resourceRecords()) {
          values.add(resourceRecord.value());
        }
        RecordSet recordSet = new RecordSet(values.toArray(new String[0]),
            resourceRecordSet.ttl());
        String name = StringUtils.stripEnd(resourceRecordSet.name(), ".");
        existing.put(name, recordSet);
        if (rootDomain.equalsIgnoreCase(name)) {
          rootContent = StringUtils.join(values);
        }
        log.info("Find name: {}", name);
      }

      if (!response.isTruncated()) {
        break;
      }
      // Set the cursor to the next batch. From the AWS docs:
      //
      // To display the next page of results, get the values of NextRecordName,
      // NextRecordType, and NextRecordIdentifier (if any) from the response. Then submit
      // another ListResourceRecordSets request, and specify those values for
      // StartRecordName, StartRecordType, and StartRecordIdentifier.
      request.startRecordIdentifier(response.nextRecordIdentifier());
      request.startRecordName(response.nextRecordName());
      request.startRecordType(response.nextRecordType());
      page += 1;
    }

    if (rootContent != null) {
      RootEntry rootEntry = RootEntry.parseEntry(rootContent);
      this.lastSeq = rootEntry.getSeq();
    }
    return existing;
  }

  // submits the given DNS changes to Route53.
  public void submitChanges(List<Change> changes, String comment) {
    if (changes.isEmpty()) {
      log.info("No DNS changes needed");
      return;
    }

    List<List<Change>> batchChanges = splitChanges(changes, route53ChangeSizeLimit,
        route53ChangeCountLimit);

    ChangeResourceRecordSetsResponse[] responses = new ChangeResourceRecordSetsResponse[batchChanges.size()];
    for (int i = 0; i < batchChanges.size(); i++) {
      log.info("Submit {}/{} changes to Route53", i + 1, batchChanges.size());

      ChangeBatch.Builder builder = ChangeBatch.builder();
      builder.changes(batchChanges.get(i));
      builder.comment(comment + String.format(" (%d/%d)", i + 1, batchChanges.size()));

      ChangeResourceRecordSetsRequest.Builder request = ChangeResourceRecordSetsRequest.builder();
      request.changeBatch(builder.build());
      request.hostedZoneId(this.zoneId);

      responses[i] = route53Client.changeResourceRecordSets(request.build());
    }

    // Wait for all change batches to propagate.
    for (ChangeResourceRecordSetsResponse response : responses) {
      log.info("Waiting for change request {}", response.changeInfo().id());

      GetChangeRequest.Builder request = GetChangeRequest.builder();
      request.id(response.changeInfo().id());

      int count = 0;
      while (true) {
        GetChangeResponse changeResponse = route53Client.getChange(request.build());
        count += 1;
        if (changeResponse.changeInfo().status() == ChangeStatus.INSYNC || count >= maxRetryLimit) {
          break;
        }
        try {
          Thread.sleep(30 * 1000);
        } catch (InterruptedException e) {
        }
      }
    }
  }

  // computeChanges creates DNS changes for the given set of DNS discovery records.
  // records is the latest records to be put in Route53.
  // The 'existing' arg is the set of records that already exist on Route53.
  public List<Change> computeChanges(String name, Map<String, String> records,
      Map<String, RecordSet> existing) {

    List<Change> changes = new ArrayList<>();
    for (Entry<String, String> entry : records.entrySet()) {
      String path = entry.getKey();
      String value = entry.getValue();
      String newValue = splitTxt(value);

      //ttl of name in dns server do not changed with time
      long ttl = path.equalsIgnoreCase(name) ? rootTTL : treeNodeTTL;

      if (!existing.containsKey(path)) {
        log.info("Create {} = {}", path, value);
        Change change = newTXTChange(ChangeAction.CREATE, path, ttl, newValue);
        changes.add(change);
      } else {
        RecordSet recordSet = existing.get(path);
        String preValue = StringUtils.join(recordSet.values, "");

        if (!preValue.equalsIgnoreCase(newValue) || recordSet.ttl != ttl) {
          log.info("Updating {} from [{}] to [{}]", path, preValue, newValue);
          Change change = newTXTChange(ChangeAction.UPSERT, path, ttl, newValue);
          changes.add(change);
        }
      }
    }

    List<Change> deleteChanges = makeDeletionChanges(records, existing);
    changes.addAll(deleteChanges);

    sortChanges(changes);
    return changes;
  }

  // creates record changes which delete all records not contained in 'keep'
  public List<Change> makeDeletionChanges(Map<String, String> keeps,
      Map<String, RecordSet> existing) {
    List<Change> changes = new ArrayList<>();
    for (Entry<String, RecordSet> entry : existing.entrySet()) {
      String path = entry.getKey();
      RecordSet recordSet = entry.getValue();
      if (!keeps.containsKey(path)) {
        log.info("Delete {} = {}", path, StringUtils.join(existing.get(path).values, ""));
        Change change = newTXTChange(ChangeAction.DELETE, path, recordSet.ttl, recordSet.values);
        changes.add(change);
      }
    }
    return changes;
  }

  // ensures DNS changes are in leaf-added -> root-changed -> leaf-deleted order.
  public static void sortChanges(List<Change> changes) {
    changes.sort((o1, o2) -> {
      if (getChangeOrder(o1) == getChangeOrder(o2)) {
        return o1.resourceRecordSet().name().compareTo(o2.resourceRecordSet().name());
      } else {
        return getChangeOrder(o1) - getChangeOrder(o2);
      }
    });
  }

  private static int getChangeOrder(Change change) {
    switch (change.action()) {
      case CREATE:
        return 1;
      case UPSERT:
        return 2;
      case DELETE:
        return 3;
      default:
        return 4;
    }
  }

  //  splits up DNS changes such that each change batch is smaller than the given RDATA limit.
  private static List<List<Change>> splitChanges(List<Change> changes, int sizeLimit,
      int countLimit) {
    List<List<Change>> batchChanges = new ArrayList<>();

    List<Change> subChanges = new ArrayList<>();
    int batchSize = 0, batchCount = 0;
    for (Change change : changes) {
      int changeCount = getChangeCount(change);
      int changeSize = getChangeSize(change) * changeCount;

      if (batchCount + changeCount <= countLimit
          && batchSize + changeSize <= sizeLimit) {
        subChanges.add(change);
        batchCount += changeCount;
        batchSize += changeSize;
      } else {
        batchChanges.add(subChanges);
        subChanges = new ArrayList<>();
        subChanges.add(change);
        batchSize = changeSize;
        batchCount = changeCount;
      }
    }
    if (!subChanges.isEmpty()) {
      batchChanges.add(subChanges);
    }
    return batchChanges;
  }

  // returns the RDATA size of a DNS change.
  private static int getChangeSize(Change change) {
    int dataSize = 0;
    for (ResourceRecord resourceRecord : change.resourceRecordSet().resourceRecords()) {
      dataSize += resourceRecord.value().length();
    }
    return dataSize;
  }

  private static int getChangeCount(Change change) {
    if (change.action() == ChangeAction.UPSERT) {
      return 2;
    }
    return 1;
  }

  public static boolean isSameChange(Change c1, Change c2) {
    boolean isSame = c1.action().equals(c2.action())
        && c1.resourceRecordSet().ttl().longValue() == c2.resourceRecordSet().ttl().longValue()
        && c1.resourceRecordSet().name().equals(c2.resourceRecordSet().name())
        && c1.resourceRecordSet().resourceRecords().size() == c2.resourceRecordSet()
        .resourceRecords().size();
    if (!isSame) {
      return false;
    }
    List<ResourceRecord> list1 = c1.resourceRecordSet().resourceRecords();
    List<ResourceRecord> list2 = c2.resourceRecordSet().resourceRecords();
    for (int i = 0; i < list1.size(); i++) {
      if (!list1.get(i).equalsBySdkFields(list2.get(i))) {
        return false;
      }
    }
    return true;
  }

  // creates a change to a TXT record.
  public Change newTXTChange(ChangeAction action, String key, long ttl, String... values) {
    ResourceRecordSet.Builder builder = ResourceRecordSet.builder()
        .name(key)
        .type(RRType.TXT)
        .ttl(ttl);
    List<ResourceRecord> resourceRecords = new ArrayList<>();
    for (String value : values) {
      ResourceRecord.Builder builder1 = ResourceRecord.builder();
      builder1.value(value);
      resourceRecords.add(builder1.build());
    }
    builder.resourceRecords(resourceRecords);

    Change.Builder builder2 = Change.builder();
    builder2.action(action);
    builder2.resourceRecordSet(builder.build());
    return builder2.build();
  }

  // splits value into a list of quoted 255-character strings.
  // only used in CREATE and UPSERT
  private String splitTxt(String value) {
    StringBuilder sb = new StringBuilder();
    while (value.length() > 253) {
      sb.append("\"" + value.substring(0, 253) + "\"");
      value = value.substring(253);
    }
    if (value.length() > 0) {
      sb.append("\"" + value + "\"");
    }
    return sb.toString();
  }

  private boolean isSubdomain(String sub, String root) {
    String subNoSuffix = StringUtils.stripEnd(sub, ".");
    String rootNoSuffix = StringUtils.stripEnd(root, ".");
    return subNoSuffix.endsWith(rootNoSuffix);
  }

  public static class RecordSet {

    String[] values;
    long ttl;

    public RecordSet(String[] values, long ttl) {
      this.values = values;
      this.ttl = ttl;
    }
  }
}
