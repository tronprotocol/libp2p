package org.tron.p2p.dns.update;


import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import software.amazon.awssdk.regions.Region;

@Data
public class PublishConfig {

  private boolean dnsPublishEnable = false;
  private String dnsPrivate = null;
  private List<String> knownTreeUrls = new ArrayList<>();
  private String dnsDomain = null;
  private DnsType dnsType = null;
  private String accessKeyId = null;
  private String accessKeySecret = null;
  private String aliDnsEndpoint = null; //for aliYun
  private String awsHostZoneId = null; //for aws
  private Region awsRegion = null; //for aws
}
