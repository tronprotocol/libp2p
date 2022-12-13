package org.tron.p2p.dns;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Discover;
import org.tron.p2p.utils.NetUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "net")
public class SyncService {

  private static final Cache<String, Discover.DnsNode> dnsNodeCache = CacheBuilder
    .newBuilder().maximumSize(2000).build();

  @Getter
  private volatile List<Node> nodes;

  @Getter
  private volatile List<Discover.DnsNode> dnsNodes;

  private volatile Discover.DnsRoot dnsRoot;

  private volatile boolean finishSync;

  private int delay = 120;

  private final ScheduledExecutorService executor =
    Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "DnsSync"));

  public void init() {
    executor.scheduleWithFixedDelay(() -> {
      try {
        work();
      } catch (Exception t) {
        log.error("Exception in dns sync task, {}", t.getMessage());
      }
    }, 2, delay, TimeUnit.SECONDS);
  }

  public void close() {
    executor.shutdown();
  }

  public void work() throws IOException {
    Discover.DnsRoot root = Client.queryDnsRoot(Parameter.p2pConfig.getDnsDomain());
    if (root == null || (dnsRoot != null && dnsRoot.getSeq() == root.getSeq() && finishSync)) {
      return;
    }
    this.dnsRoot = root;
    this.finishSync = false;
    Tree tree = new Tree(null, root.getDnsNode());
    Queue<Tree> queue = new LinkedList<>();
    queue.add(tree);
    List<Discover.DnsNode> dnsNodes = new ArrayList<>();
    load(queue, dnsNodes);
    this.dnsNodes = dnsNodes;
    this.nodes = covert(dnsNodes);
    finishSync = true;
  }

  public void load(Queue<Tree> queue, List<Discover.DnsNode> dnsNodes) throws IOException {
    while (queue.size() > 0) {
      Tree father = queue.poll();
      for(ByteString v: father.getDnsNode().getKeysList()) {
        String key = v.toString();
        Discover.DnsNode dnsNode = dnsNodeCache.getIfPresent(key);
        if (dnsNode == null) {
          dnsNode = Client.queryDnsNode(Parameter.p2pConfig.getDnsDomain(), key);
          if (dnsNode != null) {
            dnsNodeCache.put(key, dnsNode);
          }
        }
        if (dnsNode != null) {
          Tree child = new Tree(father, dnsNode);
          father.getChildren().add(child);
          if (!child.getDnsNode().getKeysList().isEmpty()) {
            queue.add(child);
          } else {
            dnsNodes.add(dnsNode);
          }
        }
      }
    }
  }

  private static List<Node> covert(List<Discover.DnsNode> dnsNodes) {
    List<Node> nodes = new ArrayList<>();
    for (Discover.DnsNode dnsNode: dnsNodes) {
      dnsNode.getNodesList().forEach(v -> nodes.add(NetUtil.getNode(v)));
    }
    return nodes;
  }

}
