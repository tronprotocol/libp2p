package org.tron.p2p.connection.tcp;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.discover.NodeHandler;
import org.tron.p2p.discover.NodeManager;

@Slf4j(topic = "net")
public class SyncPool {

  private final List<PeerConnection> activePeers = Collections
      .synchronizedList(new ArrayList<>());
  private Cache<NodeHandler, Long> nodeHandlerCache = CacheBuilder.newBuilder()
      .maximumSize(1000).expireAfterWrite(180, TimeUnit.SECONDS).recordStats().build();
  private final AtomicInteger passivePeersCount = new AtomicInteger(0);
  private final AtomicInteger activePeersCount = new AtomicInteger(0);

  @Autowired
  private NodeManager nodeManager;

  @Autowired
  private ApplicationContext ctx;

  public static volatile P2pConfig p2pConfig;

  private ChannelManager channelManager;

  private ScheduledExecutorService poolLoopExecutor = Executors.newSingleThreadScheduledExecutor();

  private ScheduledExecutorService logExecutor = Executors.newSingleThreadScheduledExecutor();

  private PeerClient peerClient;

  private int disconnectTimeout = 60_000;


  public void init() {

    channelManager = ctx.getBean(ChannelManager.class);

    peerClient = ctx.getBean(PeerClient.class);

    poolLoopExecutor.scheduleWithFixedDelay(() -> {
      try {
        check();
        fillUp();
      } catch (Throwable t) {
        log.error("Exception in sync worker", t);
      }
    }, 100, 3600, TimeUnit.MILLISECONDS);

//    logExecutor.scheduleWithFixedDelay(() -> {
//      try {
//        logActivePeers();
//      } catch (Throwable t) {
//        log.error("Exception in sync worker", t);
//      }
//    }, 30, 10, TimeUnit.SECONDS);
  }

  private void check() {
    for (PeerConnection peer : new ArrayList<>(activePeers)) {
      long now = System.currentTimeMillis();
      long disconnectTime = peer.getDisconnectTime();
      if (disconnectTime != 0 && now - disconnectTime > disconnectTimeout) {
        log.warn("Notify disconnect peer {}.", peer.getInetAddress());
        channelManager.notifyDisconnect(peer);
      }
    }
  }

  private void fillUp() {
    List<NodeHandler> connectNodes = new ArrayList<>();
    Set<InetAddress> addressInUse = new HashSet<>();
    Set<String> nodesInUse = new HashSet<>();
    channelManager.getActivePeers().forEach(channel -> {
      nodesInUse.add(channel.getPeerId());
      addressInUse.add(channel.getInetAddress());
    });

    channelManager.getActiveNodes().forEach((address, node) -> {
      nodesInUse.add(node.getHexId());
      if (!addressInUse.contains(address)) {
        connectNodes.add(nodeManager.getNodeHandler(node));
      }
    });

    int size = Math.max(p2pConfig.getMinConnections() - activePeers.size(),
        p2pConfig.getMinActiveConnections() - activePeersCount.get());
    int lackSize = size - connectNodes.size();
    if (lackSize > 0) {
      nodesInUse.add(nodeManager.getPublicHomeNode().getHexId());
      List<NodeHandler> newNodes = nodeManager.getNodes(new NodeSelector(nodesInUse), lackSize);
      connectNodes.addAll(newNodes);
    }

    connectNodes.forEach(n -> {
      peerClient.connectAsync(n, false);
      nodeHandlerCache.put(n, System.currentTimeMillis());
    });
  }

//  synchronized void logActivePeers() {
//    String str = String.format("\n\n============ Peer stats: all %d, active %d, passive %d\n\n",
//        channelManager.getActivePeers().size(), activePeersCount.get(), passivePeersCount.get());
//    metric(channelManager.getActivePeers().size(), MetricLabels.Gauge.PEERS_ALL);
//    metric(activePeersCount.get(), MetricLabels.Gauge.PEERS_ACTIVE);
//    metric(passivePeersCount.get(), MetricLabels.Gauge.PEERS_PASSIVE);
//    StringBuilder sb = new StringBuilder(str);
//    int valid = 0;
//    for (PeerConnection peer : new ArrayList<>(activePeers)) {
//      sb.append(peer.log());
//      appendPeerLatencyLog(sb, peer);
//      sb.append("\n");
//      if (!(peer.isNeedSyncFromUs() || peer.isNeedSyncFromPeer())) {
//        valid++;
//      }
//    }
//    metric(valid, MetricLabels.Gauge.PEERS_VALID);
//    logger.info(sb.toString());
//  }

//  private void metric(double amt, String peerType) {
//    Metrics.gaugeSet(MetricKeys.Gauge.PEERS, amt, peerType);
//  }
//
//  private void appendPeerLatencyLog(StringBuilder builder, PeerConnection peer) {
//    Snapshot peerSnapshot = MetricsUtil.getHistogram(MetricsKey.NET_LATENCY_FETCH_BLOCK
//        + peer.getNode().getHost()).getSnapshot();
//    builder.append(String.format(
//        "top99 : %f, top95 : %f, top75 : %f, max : %d, min : %d, mean : %f, median : %f",
//        peerSnapshot.get99thPercentile(), peerSnapshot.get95thPercentile(),
//        peerSnapshot.get75thPercentile(), peerSnapshot.getMax(), peerSnapshot.getMin(),
//        peerSnapshot.getMean(), peerSnapshot.getMedian())).append("\n");
//  }

  public List<PeerConnection> getActivePeers() {
    List<PeerConnection> peers = Lists.newArrayList();
    for (PeerConnection peer : new ArrayList<>(activePeers)) {
      if (!peer.isDisconnect()) {
        peers.add(peer);
      }
    }
    return peers;
  }

  public synchronized void onConnect(Channel peer) {
    PeerConnection peerConnection = (PeerConnection) peer;
    if (!activePeers.contains(peerConnection)) {
      if (!peerConnection.isActive()) {
        passivePeersCount.incrementAndGet();
      } else {
        activePeersCount.incrementAndGet();
      }
      activePeers.add(peerConnection);
//      activePeers
//          .sort(Comparator.comparingDouble(
//              c -> c.getNodeStatistics().pingMessageLatency.getAvg()));
      peerConnection.onConnect();
    }
  }

  public synchronized void onDisconnect(Channel peer) {
    PeerConnection peerConnection = (PeerConnection) peer;
    if (activePeers.contains(peerConnection)) {
      if (!peerConnection.isActive()) {
        passivePeersCount.decrementAndGet();
      } else {
        activePeersCount.decrementAndGet();
      }
      activePeers.remove(peerConnection);
      peerConnection.onDisconnect();
    }
  }

  public boolean isCanConnect() {
    return passivePeersCount.get()
        < p2pConfig.getMinConnections() - p2pConfig.getMinActiveConnections();
  }

  public void close() {
    try {
      activePeers.forEach(p -> {
        if (!p.isDisconnect()) {
          p.close();
        }
      });
      poolLoopExecutor.shutdownNow();
      logExecutor.shutdownNow();
    } catch (Exception e) {
      log.warn("Problems shutting down executor", e);
    }
  }

  public AtomicInteger getPassivePeersCount() {
    return passivePeersCount;
  }

  public AtomicInteger getActivePeersCount() {
    return activePeersCount;
  }

  class NodeSelector implements Predicate<NodeHandler> {

    private Set<String> nodesInUse;

    public NodeSelector(Set<String> nodesInUse) {
      this.nodesInUse = nodesInUse;
    }

    @Override
    public boolean test(NodeHandler handler) {
      long headNum = chainBaseManager.getHeadBlockNum();
      InetAddress inetAddress = handler.getInetSocketAddress().getAddress();
      Protocol.HelloMessage message = channelManager.getHelloMessageCache()
          .getIfPresent(inetAddress.getHostAddress());
      return !((handler.getNode().getHost().equals(nodeManager.getPublicHomeNode().getHost())
          && handler.getNode().getPort() == nodeManager.getPublicHomeNode().getPort())
          || (channelManager.getRecentlyDisconnected().getIfPresent(inetAddress) != null)
          || (channelManager.getBadPeers().getIfPresent(inetAddress) != null)
          || (channelManager.getConnectionNum(inetAddress) >= p2pConfig.getMaxConnectionsWithSameIp())
          || (nodesInUse.contains(handler.getNode().getHexId()))
          || (nodeHandlerCache.getIfPresent(handler) != null)
          || (message != null && headNum < message.getLowestBlockNum()));
    }
  }
}
