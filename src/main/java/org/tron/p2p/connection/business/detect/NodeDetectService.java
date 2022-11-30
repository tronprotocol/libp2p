package org.tron.p2p.connection.business.detect;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.business.MessageProcess;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.message.detect.StatusMessage;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;

@Slf4j(topic = "net")
public class NodeDetectService implements MessageProcess {

  private PeerClient peerClient;

//  private Queue<NodeStat> queue = new LinkedBlockingDeque<>();

  private Map<InetSocketAddress, NodeStat> nodeStatMap = new ConcurrentHashMap<>();
//
//  @Getter
//  private static final Cache<InetSocketAddress, NodeStat> nodesCache = CacheBuilder
//    .newBuilder().maximumSize(2000).build();

  @Getter
  private static final Cache<InetAddress, Long> badNodesCache = CacheBuilder
    .newBuilder().maximumSize(2000).build();

  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

  private final long NODE_DETECT_TIME_THRESHOLD = 5 * 60 * 1000;

  private final long NODE_DETECT_TIMEOUT = 2 * 1000;

  private final int MAX_NODE_SLOW_DETECT = 3;

  private final int MAX_NODE_NORMAL_DETECT = 10;

  private final int MAX_NODE_FAST_DETECT = 100;

  private final int MAX_NODES = 300;

  private final int MIN_NODES = 200;


  public void init(PeerClient peerClient) {
    this.peerClient = peerClient;
    executor.scheduleWithFixedDelay(() -> {
      try {
        work();
      } catch (Exception t) {
        log.error("Exception in node detect worker, {}", t.getMessage());
      }
    }, 1, 5, TimeUnit.SECONDS);
  }

  public void close() {
    executor.shutdown();
  }

  public void work() {
    log.info("##### Detect service work, map-size: {}", nodeStatMap.size());
    if (nodeStatMap.size() < MIN_NODES) {
      loadNodes();
      return;
    }

    List<NodeStat> nodeStats = getSortedNodeStats();
    NodeStat nodeStat = getSortedNodeStats().get(0);
    int n = MAX_NODE_NORMAL_DETECT;
    if (nodeStat.getLastDetectTime() < System.currentTimeMillis() - NODE_DETECT_TIME_THRESHOLD) {
      n = MAX_NODE_SLOW_DETECT;
    }

    for(int i = 0; i < n; i++) {
      detect(nodeStats.get(i));
    }
  }



  private void loadNodes() {
    int size = nodeStatMap.size();
    int count = 0;
    for (Node node: NodeManager.getConnectableNodes()) {
      InetSocketAddress socketAddress = node.getPreferInetSocketAddress();
      log.info("##### Detect loadNodes {}, count:{},{},{}", socketAddress,
        count, nodeStatMap.get(socketAddress),
        badNodesCache.getIfPresent(socketAddress.getAddress()));
      if (socketAddress != null
        && !nodeStatMap.containsKey(socketAddress)
        && badNodesCache.getIfPresent(socketAddress.getAddress()) == null) {
        NodeStat nodeStat = new NodeStat(node);
        nodeStatMap.put(socketAddress, nodeStat);
        detect(nodeStat);
        count++;
        if (count >= MAX_NODE_FAST_DETECT || count + size >= MAX_NODES) {
          break;
        }
      }
    }
    log.info("##### Detect loadNodes count:{}", count);
    Node node;
    if(Parameter.p2pConfig.getPort() == 1000) {
      node = new Node(new InetSocketAddress("127.0.0.1",  1001) );
    } else {
      node = new Node(new InetSocketAddress("127.0.0.1",  1000) );
    }
    log.info("### {}", node.getPreferInetSocketAddress());
    detect(new NodeStat(node));
  }

  private void detect(NodeStat stat) {
    try {
      log.info("##### Detect node:{}", stat.getNode());
      stat.setTotalCount(stat.getTotalCount() + 1);
      setLastDetectTime(stat);
      peerClient.connectAsync(stat.getNode(), true);
    } catch (Exception e) {
      log.warn("Detect node {} failed, {}",
        stat.getNode().getPreferInetSocketAddress(), e.getMessage());
      nodeStatMap.remove(stat.getSocketAddress());
    }
  }

  public synchronized void processMessage(Channel channel, Message message) {
    StatusMessage statusMessage = (StatusMessage)message;

    log.info("##### Receive status message from {}, {}", channel.getInetAddress(), statusMessage);

    if(!channel.isActive()) {
      channel.setDiscoveryMode(true);
      channel.send(new StatusMessage());
      channel.getCtx().close();
      return;
    }

    InetSocketAddress socketAddress = channel.getInetSocketAddress();
    NodeStat nodeStat = nodeStatMap.get(socketAddress);
    if (nodeStat == null) {
      log.warn("##### Receive status message from {} with on obj", channel.getInetAddress());
      return;
    }

    long cost = System.currentTimeMillis() - nodeStat.getLastDetectTime();
    if(cost  > NODE_DETECT_TIMEOUT
      || statusMessage.getRemainConnections() == 0) {
      log.warn("##### Receive status message from {} cost {}ms", channel.getInetAddress(), cost);
      badNodesCache.put(socketAddress.getAddress(), cost);
      nodeStatMap.remove(socketAddress);
    }

    nodeStat.setLastSuccessDetectTime(nodeStat.getLastDetectTime());
    setStatusMessage(nodeStat, statusMessage);

    channel.getCtx().close();
  }

  public void notifyDisconnect(Channel channel) {

    log.info("##### Detect channel close:{}", channel.getInetSocketAddress());

    if(!channel.isActive()) {
      return;
    }

    InetSocketAddress socketAddress = channel.getInetSocketAddress();
    if (socketAddress == null) {
      return;
    }

    NodeStat nodeStat = nodeStatMap.get(socketAddress);
    if (nodeStat == null) {
      return;
    }

    if(nodeStat.getLastDetectTime() != nodeStat.getLastSuccessDetectTime()) {
      log.info("##### Different time {}", channel.getInetSocketAddress());
      badNodesCache.put(socketAddress.getAddress(), System.currentTimeMillis());
      nodeStatMap.remove(socketAddress);
    }
  }

  private synchronized List<NodeStat> getSortedNodeStats() {
    List<NodeStat> nodeStats = new ArrayList<>(nodeStatMap.values());
    nodeStats.sort(Comparator.comparingLong(o -> o.getLastDetectTime()));
    return nodeStats;
  }

  private synchronized void setLastDetectTime(NodeStat nodeStat) {
    nodeStat.setLastDetectTime(System.currentTimeMillis());
  }

  private synchronized void setStatusMessage(NodeStat nodeStat, StatusMessage message) {
    nodeStat.setStatusMessage(message);
  }

  public synchronized List<Node> getConnectableNodes() {
    List<NodeStat> stats = new ArrayList<>();
    List<Node> nodes = new ArrayList<>();
    nodeStatMap.values().forEach(stat -> {
      if (stat.getStatusMessage() != null) {
        stats.add(stat);
      }
    });

    if (stats.isEmpty()) {
      return nodes;
    }

    stats.sort(Comparator.comparingInt(o -> -o.getStatusMessage().getRemainConnections()));
    stats.forEach(stat -> nodes.add(stat.getNode()));
    return nodes;
  }

}
