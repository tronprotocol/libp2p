package org.tron.p2p.dns.update;

import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.dns.tree.Tree;
import org.tron.p2p.exception.DnsException;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "net")
public class PublishService {

  private final long publishDelay = 24 * 60 * 60;
  private ScheduledExecutorService publisher = Executors.newSingleThreadScheduledExecutor();

  public void init() {
    if (checkConfig(Parameter.p2pConfig)) {
      publisher.scheduleWithFixedDelay(() -> {
        startPublish();
      }, 300, publishDelay, TimeUnit.SECONDS);
    }
  }

  private void startPublish() {
    try {
      if (Parameter.p2pConfig.getDnsServer().equals("alidns")) {
        publishAliDns();
      }
    } catch (Exception e) {
      log.warn("Failed to publish dns, error msg: {}", e.getMessage());
    }
  }

  public void publishAliDns() throws Exception {
    P2pConfig config = Parameter.p2pConfig;
    try {
      AliClient client = new AliClient(config.getDnsEndpoint(),
          config.getAccessKeyId(),
          config.getAccessKeySecret());
      Tree tree = new Tree();
      List<String> nodes = getNodes();
      tree = tree.makeTree(1, nodes, getLinks(), config.getDnsPrivateKey());
      client.deploy(config.getDnsDomain(), tree);
    } catch (DnsException de) {
      log.warn("{}", de.toString());
    }
  }

  private List<String> getNodes() throws UnknownHostException {
    List<Node> nodes = NodeManager.getConnectableNodes();
    List<DnsNode> dnsNodes = new ArrayList<>();
    for (Node node : nodes) {
      DnsNode dnsNode = new DnsNode(node.getId(), node.getHostV4(), node.getHostV6(), node.getPort());
      dnsNodes.add(dnsNode);
    }
    return Tree.merge(dnsNodes);
  }

  private List<String> getLinks() {
    // todo
    return new ArrayList<>();
  }

  private boolean checkConfig(P2pConfig config) {
    if (!config.isDnsPublishEnable()) {
      return false;
    }
    if (config.getDnsServer() == null || config.getDnsDomain() == null ||
        !(config.getDnsServer().equals("alidns") || config.getDnsServer().equals("awsroute53"))) {
      log.error("The dns server type must be specified when the dns publishing function is enabled");
      return false;
    }
    if (config.getDnsServer().equals("alidns") &&
        (config.getDnsEndpoint() == null ||
            config.getAccessKeyId() == null ||
            config.getAccessKeySecret() == null)) {
      log.error("The configuration items related to the Aliyun dns server cannot be empty");
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
