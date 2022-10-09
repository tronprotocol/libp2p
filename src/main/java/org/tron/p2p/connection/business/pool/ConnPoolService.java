package org.tron.p2p.connection.business.pool;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
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
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.utils.CollectionUtils;

@Slf4j(topic = "net")
public class ConnPoolService extends P2pEventHandler {

  private final List<Channel> activePeers = Collections.synchronizedList(new ArrayList<>());
  private Cache<InetAddress, Long> peerClientCache = CacheBuilder.newBuilder()
      .maximumSize(1000).expireAfterWrite(180, TimeUnit.SECONDS).recordStats().build();
  @Getter
  private final AtomicInteger passivePeersCount = new AtomicInteger(0);
  @Getter
  private final AtomicInteger activePeersCount = new AtomicInteger(0);
  private final ScheduledExecutorService poolLoopExecutor = Executors.newSingleThreadScheduledExecutor();
  private final ScheduledExecutorService disconnectExecutor = Executors.newSingleThreadScheduledExecutor();

  public P2pConfig p2pConfig = Parameter.p2pConfig;
  private PeerClient peerClient;

  public ConnPoolService() {
    this.types = new ArrayList<>(); //no message type registers
    Parameter.addP2pEventHandle(this);
  }

  public void init(PeerClient peerClient) {
    this.peerClient = peerClient;
    poolLoopExecutor.scheduleWithFixedDelay(() -> {
      try {
        connect(NodeManager.getConnectableNodes());
      } catch (Exception t) {
        log.error("Exception in poolLoopExecutor worker", t);
      }
    }, 100, 3600, TimeUnit.MILLISECONDS);

    if (p2pConfig.isDisconnectionPolicyEnable()) {
      disconnectExecutor.scheduleWithFixedDelay(() -> {
        try {
          check();
        } catch (Exception t) {
          log.error("Exception in disconnectExecutor worker", t);
        }
      }, 30, 30, TimeUnit.SECONDS);
    }
  }

  private void connect(List<Node> connectableNodes) {
    List<Node> connectNodes = new ArrayList<>();

    //collect already used nodes in channelManager
    Set<InetAddress> addressInUse = new HashSet<>();
    Set<String> nodesInUse = new HashSet<>();
    ChannelManager.getChannels().values().forEach(channel -> {
      if (channel.getNodeId() != null) {
        nodesInUse.add(channel.getNodeId());
      }
      addressInUse.add(channel.getInetAddress());
    });

    //first choose from active nodes that not used
    p2pConfig.getActiveNodes().forEach(address -> {
      if (!addressInUse.contains(address.getAddress())) {
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
      List<Node> newNodes = getNodes(nodesInUse, connectableNodes, lackSize);
      connectNodes.addAll(newNodes);
    }

    log.info("lackSize:{}, connectNodes:{}", lackSize, connectNodes.size());
    //establish tcp connection with chose nodes by peerClient
    connectNodes.forEach(n -> {
      peerClient.connectAsync(n, false);
      //send a message and wait for response ? now we have not invoke onConnect/onDisconnect, so activePeers not changed
      peerClientCache.put(n.getInetSocketAddress().getAddress(), System.currentTimeMillis());
    });
  }

  public List<Node> getNodes(Set<String> nodesInUse, List<Node> connectableNodes, int limit) {
    List<Node> filtered = new ArrayList<>();
    long now = System.currentTimeMillis();
    for (Node node : connectableNodes) {
      InetAddress inetAddress = node.getInetSocketAddress().getAddress();
      Long forbiddenTime = ChannelManager.getBannedNodes().getIfPresent(inetAddress);
      if ((node.getHost().equals(p2pConfig.getIp()) && node.getPort() == p2pConfig.getPort())
          || (forbiddenTime != null && now <= forbiddenTime)
          || (ChannelManager.getConnectionNum(inetAddress)
          >= p2pConfig.getMaxConnectionsWithSameIp())
          || (nodesInUse.contains(node.getHexId()))
          || (peerClientCache.getIfPresent(inetAddress) != null)) {
        continue;
      }
      filtered.add(node);
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
    Collection<Channel> peers = activePeers.stream()
        .filter(peer -> !peer.isDisconnect())
        .filter(peer -> !peer.isTrustPeer())
        .filter(peer -> !peer.isActive())
        .collect(Collectors.toList());

    // if len(peers) >= 0, disconnect randomly
    if (!peers.isEmpty()) {
      List<Channel> list = new ArrayList<>(peers);
      Channel peer = list.get(new Random().nextInt(peers.size()));
      peer.close();
    }
  }

  private synchronized void logActivePeers() {
    String str = String.format("Peer stats: all %d, active %d, passive %d",
        ChannelManager.getChannels().size(), activePeersCount.get(), passivePeersCount.get());
    log.info(str);
  }

  @Override
  public synchronized void onConnect(Channel peer) {
    log.info("ConnPoolService onConnect");
    if (!activePeers.contains(peer)) {
      if (!peer.isActive()) {
        passivePeersCount.incrementAndGet();
      } else {
        activePeersCount.incrementAndGet();
      }
      activePeers.add(peer);
    }
    logActivePeers();
  }

  @Override
  public synchronized void onDisconnect(Channel peer) {
    log.info("ConnPoolService onDisconnect");
    if (activePeers.contains(peer)) {
      if (!peer.isActive()) {
        passivePeersCount.decrementAndGet();
      } else {
        activePeersCount.decrementAndGet();
      }
      activePeers.remove(peer);
    }
    logActivePeers();
  }

  @Override
  public void onMessage(Channel channel, byte[] data) {
  }

  public void close() {
    try {
      activePeers.forEach(p -> {
        if (!p.isDisconnect()) {
          p.close();
        }
      });
      poolLoopExecutor.shutdownNow();
    } catch (Exception e) {
      log.warn("Problems shutting down executor", e);
    }
  }
}
