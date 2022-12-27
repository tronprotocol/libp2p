package org.tron.p2p.dns.update;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.Tree;
import software.amazon.awssdk.regions.Region;

@Slf4j(topic = "net")
public class PublishService {

  private static final long publishDelay = 3 * 60 * 60;

  private ScheduledExecutorService publisher = Executors.newSingleThreadScheduledExecutor();

  public void init() {
    if (checkConfig(Parameter.p2pConfig)) {
      publisher.scheduleWithFixedDelay(this::startPublish, 300, publishDelay, TimeUnit.SECONDS);
    }
  }

  private void startPublish() {
    P2pConfig config = Parameter.p2pConfig;
    try {
      Publish publish;
      if (config.getDnsType() == DnsType.AliYun) {
        publish = new AliClient(config.getAliDnsEndpoint(),
            config.getAliAccessKeyId(),
            config.getAliAccessKeySecret());
      } else {
        publish = new AwsClient(config.getAwsAccessKeyId(),
            config.getAwsAccessKeySecret(),
            config.getAwsHostZoneId(),
            Region.of(config.getAwsRegion()));
      }
      Tree tree = new Tree();
      List<String> nodes = getNodes();
      tree = tree.makeTree(1, nodes, config.getKnownEnrTreeUrls(), config.getDnsPrivate());
      publish.deploy(config.getDnsDomain(), tree);
    } catch (Exception e) {
      log.warn("Failed to publish dns, error msg: {}", e.getMessage());
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

  private boolean checkConfig(P2pConfig config) {
    //we must enable discover service before we can publish dns service
    if (!config.isDiscoverEnable() || !config.isDnsPublishEnable()) {
      log.info("Discover service is disable or dns publish service is disable");
      return false;
    }
    if (config.getDnsType() == null) {
      log.error("The dns server type must be specified when dns publish service is enable");
      return false;
    }
    if (config.getDnsType() == DnsType.AliYun &&
        (config.getAliDnsEndpoint() == null ||
            config.getAliAccessKeyId() == null ||
            config.getAliAccessKeySecret() == null)) {
      log.error("The configuration items related to the Aliyun dns server cannot be empty");
      return false;
    }
    if (config.getDnsType() == DnsType.AwsRoute53 &&
        (config.getAwsHostZoneId() == null ||
            config.getAliAccessKeyId() == null ||
            config.getAwsAccessKeySecret() == null)) {
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
