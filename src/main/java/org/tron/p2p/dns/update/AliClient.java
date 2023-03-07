package org.tron.p2p.dns.update;

import com.aliyun.alidns20150109.Client;
import com.aliyun.alidns20150109.models.*;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord;
import com.aliyun.teaopenapi.models.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.dns.tree.LinkEntry;
import org.tron.p2p.dns.tree.RootEntry;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j(topic = "net")
public class AliClient implements Publish {

  private final Long domainRecordsPageSize = 20L;
  private final int maxRetryCount = 3;
  private final int successCode = 200;
  private final long retryWaitTime = 30;
  private final int treeNodeTTL = 24 * 60 * 60;
  private int lastSeq = 0;
  private final Client aliDnsClient;
  private double changeThreshold;

  public AliClient(String endpoint, String accessKeyId, String accessKeySecret,
      double changeThreshold) throws Exception {
    Config config = new Config();
    config.accessKeyId = accessKeyId;
    config.accessKeySecret = accessKeySecret;
    config.endpoint = endpoint;
    this.changeThreshold = changeThreshold;
    aliDnsClient = new Client(config);
  }

  @Override
  public void testConnect() throws Exception {
  }

  @Override
  public void deploy(String domainName, Tree t) throws DnsException {
    try {
      Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> existing = collectRecords(
          domainName);
      String represent = LinkEntry.buildRepresent(t.getBase32PublicKey(), domainName);
      log.info("Trying to publish {}", represent);
      t.setSeq(this.lastSeq + 1);
      t.sign(); //seq changed, wo need to sign again
      Map<String, String> records = t.toTXT(null);
      submitChanges(domainName, records, existing);
    } catch (Exception e) {
      throw new DnsException(DnsException.TypeEnum.DEPLOY_DOMAIN_FAILED, e);
    }
  }

  @Override
  public boolean deleteDomain(String domainName) throws Exception {
    DeleteSubDomainRecordsRequest request = new DeleteSubDomainRecordsRequest();
    request.setDomainName(domainName);
    DeleteSubDomainRecordsResponse response = aliDnsClient.deleteSubDomainRecords(request);
    return response.statusCode == successCode;
  }

  // collects all TXT records below the given name. it also update lastSeq
  @Override
  public Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> collectRecords(
      String domain) throws Exception {
    Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> records = new HashMap<>();

    String rootContent = null;
    try {
      DescribeDomainRecordsRequest request = new DescribeDomainRecordsRequest();
      request.setDomainName(domain);
      request.setType("TXT");
      request.setPageSize(domainRecordsPageSize);
      Long currentPageNum = 1L;
      while (true) {
        request.setPageNumber(currentPageNum);
        DescribeDomainRecordsResponse response = aliDnsClient.describeDomainRecords(request);
        if (response.statusCode == successCode) {
          for (DescribeDomainRecordsResponseBodyDomainRecordsRecord r : response.getBody()
              .getDomainRecords().getRecord()) {
            String name = StringUtils.stripEnd(r.getRR(), ".");
            records.put(name, r);
            if (domain.equalsIgnoreCase(name)) {
              rootContent = r.value;
            }
          }
          if (currentPageNum * domainRecordsPageSize >= response.getBody().getTotalCount()) {
            break;
          }
          currentPageNum++;
        } else {
          throw new Exception("Failed to request domain records");
        }
      }
    } catch (Exception e) {
      log.warn("Failed to collect domain records, error msg: {}", e.getMessage());
      throw e;
    }

    if (rootContent != null) {
      RootEntry rootEntry = RootEntry.parseEntry(rootContent);
      this.lastSeq = rootEntry.getSeq();
    }

    return records;
  }

  private void submitChanges(String domainName,
      Map<String, String> records,
      Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> existing)
      throws Exception {
    if (!computeChanges(domainName, records, existing)) {
      return;
    }
    long ttl;
    long addCount = 0;
    long updateCount = 0;
    long deleteCount = 0;
    for (Map.Entry<String, String> entry : records.entrySet()) {
      boolean result = true;
      ttl = treeNodeTTL;
      if (entry.getKey().equals(domainName)) {
        ttl = rootTTL;
      }
      if (!existing.containsKey(entry.getKey())) {
        result = addRecord(domainName, entry.getKey(), entry.getValue(), ttl);
        addCount++;
      } else if (!entry.getValue().equals(existing.get(entry.getKey()).getValue()) ||
          existing.get(entry.getKey()).getTTL() != ttl) {
        result = updateRecord(existing.get(entry.getKey()).getRecordId(), entry.getKey(),
            entry.getValue(), ttl);
        updateCount++;
      }

      if (!result) {
        throw new Exception("Adding or updating record failed");
      }
    }

    for (String key : existing.keySet()) {
      if (!records.containsKey(key)) {
        deleteRecord(existing.get(key).getRecordId());
        deleteCount++;
      }
    }
    log.debug("Published successfully, add count:{}, update count:{}, delete count:{}",
        addCount, updateCount, deleteCount);
  }

  private boolean computeChanges(String domainName,
      Map<String, String> records,
      Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> existing) {
    long ttl;
    long changeCount = 0;
    for (Map.Entry<String, String> entry : records.entrySet()) {
      ttl = treeNodeTTL;
      if (entry.getKey().equals(domainName)) {
        ttl = rootTTL;
      }
      if (!existing.containsKey(entry.getKey())) {
        changeCount++;
      } else if (!entry.getValue().equals(existing.get(entry.getKey()).getValue()) ||
          existing.get(entry.getKey()).getTTL() != ttl) {
        changeCount++;
      }
    }

    for (String key : existing.keySet()) {
      if (!records.containsKey(key)) {
        changeCount++;
      }
    }

    if (changeCount > 0
        && (existing.size() == 0 || (double) changeCount / existing.size() > changeThreshold)) {
      log.info("change count: {}, existing count: {}",
          changeCount, existing.size());
      return true;
    } else {
      log.info("The number of changed data is too small, change count: {}, existing count: {}",
          changeCount, existing.size());
      return false;
    }
  }

  public boolean addRecord(String domainName, String RR, String value, long ttl) throws Exception {
    AddDomainRecordRequest request = new AddDomainRecordRequest();
    request.setDomainName(domainName);
    request.setRR(RR);
    request.setType("TXT");
    request.setValue(value);
    request.setTTL(ttl);
    int retryCount = 0;
    while (true) {
      AddDomainRecordResponse response = aliDnsClient.addDomainRecord(request);
      if (response.statusCode == successCode) {
        break;
      } else if (retryCount < maxRetryCount) {
        retryCount++;
        Thread.sleep(retryWaitTime);
      } else {
        return false;
      }
    }
    return true;
  }

  public boolean updateRecord(String recId, String RR, String value, long ttl) throws Exception {
    UpdateDomainRecordRequest request = new UpdateDomainRecordRequest();
    request.setRecordId(recId);
    request.setRR(RR);
    request.setType("TXT");
    request.setValue(value);
    request.setTTL(ttl);
    int retryCount = 0;
    while (true) {
      UpdateDomainRecordResponse response = aliDnsClient.updateDomainRecord(request);
      if (response.statusCode == successCode) {
        break;
      } else if (retryCount < maxRetryCount) {
        retryCount++;
        Thread.sleep(retryWaitTime);
      } else {
        return false;
      }
    }
    return true;
  }

  public boolean deleteRecord(String recId) throws Exception {
    DeleteDomainRecordRequest request = new DeleteDomainRecordRequest();
    request.setRecordId(recId);
    int retryCount = 0;
    while (true) {
      DeleteDomainRecordResponse response = aliDnsClient.deleteDomainRecord(request);
      if (response.statusCode == successCode) {
        break;
      } else if (retryCount < maxRetryCount) {
        retryCount++;
        Thread.sleep(retryWaitTime);
      } else {
        return false;
      }
    }
    return true;
  }

  public String getRecId(String domainName, String RR) {
    String recId = null;
    try {
      DescribeDomainRecordsRequest request = new DescribeDomainRecordsRequest();
      request.setDomainName(domainName);
      request.setRRKeyWord(RR);
      DescribeDomainRecordsResponse response = aliDnsClient.describeDomainRecords(request);
      if (response.getBody().getTotalCount() > 0) {
        List<DescribeDomainRecordsResponseBodyDomainRecordsRecord> recs =
            response.getBody().getDomainRecords().getRecord();
        for (DescribeDomainRecordsResponseBodyDomainRecordsRecord rec : recs) {
          if (rec.getRR().equalsIgnoreCase(RR)) {
            recId = rec.getRecordId();
            break;
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to get record id, error msg: {}", e.getMessage());
    }
    return recId;
  }

  public String update(String DomainName, String RR, String value, long ttl) {
    String type = "TXT";
    String recId = null;
    try {
      String existRecId = getRecId(DomainName, RR);
      if (existRecId == null || existRecId.isEmpty()) {
        AddDomainRecordRequest request = new AddDomainRecordRequest();
        request.setDomainName(DomainName);
        request.setRR(RR);
        request.setType(type);
        request.setValue(value);
        request.setTTL(ttl);
        AddDomainRecordResponse response = aliDnsClient.addDomainRecord(request);
        recId = response.getBody().getRecordId();
      } else {
        UpdateDomainRecordRequest request = new UpdateDomainRecordRequest();
        request.setRecordId(existRecId);
        request.setRR(RR);
        request.setType(type);
        request.setValue(value);
        request.setTTL(ttl);
        UpdateDomainRecordResponse response = aliDnsClient.updateDomainRecord(request);
        recId = response.getBody().getRecordId();
      }
    } catch (Exception e) {
      log.warn("Failed to update or add domain record, error mag: {}", e.getMessage());
    }

    return recId;
  }

  public boolean deleteByRR(String domainName, String RR) {
    try {
      String recId = getRecId(domainName, RR);
      if (recId != null && !recId.isEmpty()) {
        DeleteDomainRecordRequest request = new DeleteDomainRecordRequest();
        request.setRecordId(recId);
        DeleteDomainRecordResponse response = aliDnsClient.deleteDomainRecord(request);
        if (response.statusCode != successCode) {
          return false;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to delete domain record, domain name: {}, RR: {}, error msg: {}",
          domainName, RR, e.getMessage());
      return false;
    }
    return true;
  }
}
