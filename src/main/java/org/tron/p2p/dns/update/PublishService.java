package org.tron.p2p.dns.update;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.Tree;

@Slf4j(topic = "net")
public class PublishService {

  private static final long publishDelay = 1 * 60 * 60;

  private ScheduledExecutorService publisher = Executors.newSingleThreadScheduledExecutor();

  public void init() {
    if (checkConfig(Parameter.p2pConfig.getPublishConfig())) {
      publisher.scheduleWithFixedDelay(this::startPublish, 300, publishDelay, TimeUnit.SECONDS);
    }
  }

  private void startPublish() {
    PublishConfig config = Parameter.p2pConfig.getPublishConfig();
    try {
      Publish publish;
      if (config.getDnsType() == DnsType.AliYun) {
        publish = new AliClient(config.getAliDnsEndpoint(),
            config.getAccessKeyId(),
            config.getAccessKeySecret());
      } else {
        publish = new AwsClient(config.getAccessKeyId(),
            config.getAccessKeySecret(),
            config.getAwsHostZoneId(),
            config.getAwsRegion());
      }
      Tree tree = new Tree();
      List<String> nodes = getNodes();
      tree.makeTree(1, nodes, config.getKnownEnrTreeUrls(), config.getDnsPrivate());
      publish.deploy(config.getDnsDomain(), tree);
    } catch (Exception e) {
      log.error("Failed to publish dns", e);
    }
  }

  private List<String> getNodes() throws UnknownHostException {
    List<Node> nodes = NodeManager.getConnectableNodes();
    List<DnsNode> dnsNodes = new ArrayList<>();
    for (Node node : nodes) {
      DnsNode dnsNode = new DnsNode(node.getId(), node.getHostV4(), node.getHostV6(),
          node.getPort());
      dnsNodes.add(dnsNode);
    }
    return Tree.merge(dnsNodes);
  }

  private boolean checkConfig(PublishConfig config) {
    if (!config.isDnsPublishEnable()) {
      log.info("Discover service is disable or dns publish service is disable");
      return false;
    }
    if (config.getDnsType() == null) {
      log.error("The dns server type must be specified when enabling the dns publishing service");
      return false;
    }
    if (StringUtils.isEmpty(config.getDnsDomain())) {
      log.error("The dns domain must be specified when enabling the dns publishing service");
      return false;
    }
    if (config.getDnsType() == DnsType.AliYun &&
        (StringUtils.isEmpty(config.getAccessKeyId()) ||
            StringUtils.isEmpty(config.getAccessKeySecret()) ||
            StringUtils.isEmpty(config.getAliDnsEndpoint())
        )) {
      log.error("The configuration items related to the Aliyun dns server cannot be empty");
      return false;
    }
    if (config.getDnsType() == DnsType.AwsRoute53 &&
        (StringUtils.isEmpty(config.getAccessKeyId()) ||
            StringUtils.isEmpty(config.getAccessKeySecret()) ||
            config.getAwsRegion() == null)) {
      log.error("The configuration items related to the AwsRoute53 dns server cannot be empty");
      return false;
    }
    return true;
  }

  public void close() {
    if (!publisher.isShutdown()) {
      publisher.shutdown();
    }
  }
}
