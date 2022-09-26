package org.tron.p2p.connection.socket;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Lists;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.utils.CollectionUtils;

@Slf4j(topic = "net")
public class SyncPool {
  private final List<PeerConnection> activePeers = Collections.synchronizedList(new ArrayList<>());
  private Cache<InetAddress, Long> nodeHandlerCache = CacheBuilder.newBuilder()
      .maximumSize(1000).expireAfterWrite(180, TimeUnit.SECONDS).recordStats().build();
  @Getter
  private final AtomicInteger passivePeersCount = new AtomicInteger(0);
  @Getter
  private final AtomicInteger activePeersCount = new AtomicInteger(0);
  private ScheduledExecutorService poolLoopExecutor = Executors.newSingleThreadScheduledExecutor();
  private ScheduledExecutorService disconnectExecutor = Executors.newSingleThreadScheduledExecutor();
  private ScheduledExecutorService logExecutor = Executors.newSingleThreadScheduledExecutor();

  public P2pConfig p2pConfig = Parameter.p2pConfig;
  private PeerClient peerClient;

  private int disconnectTimeout = 60_000;

  public void init(PeerClient peerClient) {
    this.peerClient = peerClient;
    poolLoopExecutor.scheduleWithFixedDelay(() -> {
      try {
        connect();
      } catch (Throwable t) {
        log.error("Exception in poolLoopExecutor worker", t);
      }
    }, 100, 3600, TimeUnit.MILLISECONDS);

    if (p2pConfig.isDisconnectionPolicyEnable()) {
      disconnectExecutor.scheduleWithFixedDelay(() -> {
        try {
          check();
        } catch (Throwable t) {
          log.error("Exception in disconnectExecutor worker", t);
        }
      }, 30, 30, TimeUnit.SECONDS);
    }

    logExecutor.scheduleWithFixedDelay(() -> {
      try {
        logActivePeers();
      } catch (Throwable t) {
        log.error("Exception in logExecutor worker", t);
      }
    }, 30, 10, TimeUnit.SECONDS);
  }

  private void connect() {
    List<Node> connectNodes = new ArrayList<>();

    //collect already used nodes in channelManager
    Set<InetAddress> addressInUse = new HashSet<>();
    Set<String> nodesInUse = new HashSet<>();
    ChannelManager.getChannels().values().forEach(channel -> {
      nodesInUse.add(channel.getPeerId());
      addressInUse.add(channel.getInetAddress());
    });

    //first choose from active nodes that not used
    p2pConfig.getActiveNodes().forEach(address -> {
      if (!addressInUse.contains(address)) {
        connectNodes.add(new Node(address));
      }
    });

    //calculate lackSize
    int size = Math.max(p2pConfig.getMinConnections() - activePeers.size(),
        p2pConfig.getMinActiveConnections() - activePeersCount.get());
    int lackSize = size - connectNodes.size();

    //choose lackSize nodes from nodeManager that meet special requirement
    if (lackSize > 0) {
      nodesInUse.add(Hex.toHexString(p2pConfig.getNodeID()));
      List<Node> newNodes = getNodes(new NodeSelector(nodesInUse), lackSize);
      connectNodes.addAll(newNodes);
    }

    //establish tcp connection with chose nodes by peerClient
    connectNodes.forEach(n -> {
      peerClient.connectAsync(n, false);
      nodeHandlerCache.put(n.getInetSocketAddress().getAddress(), System.currentTimeMillis());
    });
  }

  private List<Node> getNodes(Predicate<Node> predicate, int limit) {
    List<Node> filtered = new ArrayList<>();
    for (Node node : nodeManager.getConnectableNodes()) {
      if (predicate.test(node)) {
        filtered.add(node);
      }
    }
    //order by updateTime desc
    filtered.sort(Comparator.comparingLong(node -> -node.getUpdateTime()));
    return CollectionUtils.truncate(filtered, limit);
  }

  private void check() {
    // check if active channels < maxConnections
    if (ChannelManager.getChannels().size() < p2pConfig.getMaxConnections()) {
      return;
    }

    // filter trust peer and active peer
    Collection<PeerConnection> peers = getActivePeers().stream()
        .filter(peer -> !peer.isTrustPeer())
        .filter(peer -> !peer.isActive())
        .collect(Collectors.toList());

    // if len(peers) >= 0, disconnect randomly
    if (peers.size() > 0) {
      List<PeerConnection> list = new ArrayList();
      peers.forEach(p -> list.add(p));
      PeerConnection peer = list.get(new Random().nextInt(peers.size()));
      peer.close();
    }
  }

  synchronized void logActivePeers() {
    String str = String.format("Peer stats: all %d, active %d, passive %d",
        ChannelManager.getChannels().size(), activePeersCount.get(), passivePeersCount.get());
    log.info(str);
  }

  public List<PeerConnection> getActivePeers() {
    List<PeerConnection> peers = Lists.newArrayList();
    for (PeerConnection peer : activePeers) {
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

  //business hello message?
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

  class NodeSelector implements Predicate<Node> {

    private Set<String> nodesInUse;

    public NodeSelector(Set<String> nodesInUse) {
      this.nodesInUse = nodesInUse;
    }

    @Override
    public boolean test(Node node) {

      InetAddress inetAddress = node.getInetSocketAddress().getAddress();
      return !((node.getHost().equals(nodeManager.getPublicHomeNode().getHost())
          && node.getPort() == nodeManager.getPublicHomeNode().getPort())
          || (channelManager.getRecentlyDisconnected().getIfPresent(inetAddress) != null)
          || (channelManager.getBannedNodes().getIfPresent(inetAddress) != null)
          || (channelManager.getConnectionNum(inetAddress)
          >= p2pConfig.getMaxConnectionsWithSameIp())
          || (nodesInUse.contains(node.getHexId()))
          || (nodeHandlerCache.getIfPresent(inetAddress) != null));
    }
  }
}