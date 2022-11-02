package org.tron.p2p.connection.business.pool;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.discover.protocol.kad.KadService;
import org.tron.p2p.exception.P2pException;
import org.tron.p2p.utils.CollectionUtils;

@Slf4j(topic = "net")
public class ConnPoolService extends P2pEventHandler {

  private final List<Channel> activePeers = Collections.synchronizedList(new ArrayList<>());
  private Cache<InetAddress, Long> peerClientCache = CacheBuilder.newBuilder()
      .maximumSize(1000).expireAfterWrite(120, TimeUnit.SECONDS).recordStats().build();
  @Getter
  private final AtomicInteger passivePeersCount = new AtomicInteger(0);
  @Getter
  private final AtomicInteger activePeersCount = new AtomicInteger(0);
  private final ScheduledExecutorService poolLoopExecutor = Executors.newSingleThreadScheduledExecutor();
  private final ScheduledExecutorService disconnectExecutor = Executors.newSingleThreadScheduledExecutor();

  public P2pConfig p2pConfig = Parameter.p2pConfig;
  private PeerClient peerClient;

  public ConnPoolService() {
    this.messageTypes = new HashSet<>(); //no message type registers
    try {
      Parameter.addP2pEventHandle(this);
    } catch (P2pException e) {
    }
  }

  public void init(PeerClient peerClient) {
    this.peerClient = peerClient;
    poolLoopExecutor.scheduleWithFixedDelay(() -> {
      try {
        connect();
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

  private void connect() {
    List<Node> connectNodes = new ArrayList<>();

    //collect already used nodes in channelManager
    Set<InetAddress> addressInUse = new HashSet<>();
    Set<InetSocketAddress> inetInUse = new HashSet<>();
    Set<String> nodesInUse = new HashSet<>();
    ChannelManager.getChannels().values().forEach(channel -> {
      if (StringUtils.isNotEmpty(channel.getNodeId())) {
        nodesInUse.add(channel.getNodeId());
      }
      addressInUse.add(channel.getInetAddress());
      inetInUse.add(channel.getInetSocketAddress());
      //add node's other IP stack's inetAddress
      InetSocketAddress other = KadService.matchDifferentIpStack(channel.getInetSocketAddress());
      if (other != null) {
        //log.info("exist:{}, other:{}", channel.getInetSocketAddress(), other);
        addressInUse.add(other.getAddress());
        inetInUse.add(other);
      }
    });

    p2pConfig.getActiveNodes().forEach(address -> {
      if (!addressInUse.contains(address.getAddress())) {
        addressInUse.add(address.getAddress());
        Node node = new Node(address); //use a random NodeId for config activeNodes
        connectNodes.add(node);
      }
    });

    //calculate lackSize exclude config activeNodes
    int size = Math.max(p2pConfig.getMinConnections() - activePeers.size(),
        p2pConfig.getMinActiveConnections() - activePeersCount.get());
    int lackSize = size - connectNodes.size();

    //choose lackSize nodes from nodeManager that meet special requirement
    if (lackSize > 0) {
      nodesInUse.add(Hex.toHexString(p2pConfig.getNodeID()));
      List<Node> connectableNodes = NodeManager.getConnectableNodes();
      List<Node> newNodes = getNodes(nodesInUse, inetInUse, connectableNodes, lackSize);
      connectNodes.addAll(newNodes);
    }

    //log.info("Lack size:{}, connectNodes size:{}", size, connectNodes.size());
    //establish tcp connection with chose nodes by peerClient
    connectNodes.forEach(n -> {
      if (n.isIpV4Compatible()) {
        peerClient.connectAsync(n, false, false);
        peerClientCache.put(n.getInetSocketAddressV4().getAddress(), System.currentTimeMillis());
      } else if (n.isIpV6Compatible()) {
        peerClient.connectAsync(n, true, false);
        peerClientCache.put(n.getInetSocketAddressV6().getAddress(), System.currentTimeMillis());
      } else {
        log.error("Not compatible IP Stack");
      }
    });
  }

  public List<Node> getNodes(Set<String> nodesInUse, Set<InetSocketAddress> inetInUse,
      List<Node> connectableNodes, int limit) {
    List<Node> filtered = new ArrayList<>();
    long now = System.currentTimeMillis();
    for (Node node : connectableNodes) {
      if (node.getId() != null && nodesInUse.contains(node.getHexId())) {
        continue;
      }
      if (!node.isIpStackCompatible()) {
        continue;
      }
      if (node.isIpV4Compatible()) {
        if (inetInUse.contains(node.getInetSocketAddressV4())) {
          continue;
        }
        if (node.getHostV4().equals(p2pConfig.getIp()) && node.getPort() == p2pConfig.getPort()) {
          continue;
        }
        if (filter(node.getInetSocketAddressV4().getAddress(), now)) {
          continue;
        }
      } else if (node.isIpV6Compatible()) {
        if (inetInUse.contains(node.getInetSocketAddressV6())) {
          continue;
        }
        if (node.getHostV6().equals(p2pConfig.getIpv6()) && node.getPort() == p2pConfig.getPort()) {
          continue;
        }
        if (filter(node.getInetSocketAddressV6().getAddress(), now)) {
          continue;
        }
      }
      // sometimes error occurs if update_time changes when sort, so we copy it
      filtered.add((Node) node.clone());
    }

    //order by updateTime desc.
    filtered.sort(Comparator.comparingLong(node -> -node.getUpdateTime()));
    return CollectionUtils.truncate(filtered, limit);
  }

  private boolean filter(InetAddress inetAddress, long now) {
    Long forbiddenTime = ChannelManager.getBannedNodes().getIfPresent(inetAddress);
    return (forbiddenTime != null && now <= forbiddenTime)
        || (ChannelManager.getConnectionNum(inetAddress)
        >= p2pConfig.getMaxConnectionsWithSameIp())
        || (peerClientCache.getIfPresent(inetAddress) != null);
  }

  private void check() {
    if (ChannelManager.getChannels().size() < p2pConfig.getMaxConnections()) {
      return;
    }

    Collection<Channel> peers = activePeers.stream()
        .filter(peer -> !peer.isDisconnect())
        .filter(peer -> !peer.isTrustPeer())
        .filter(peer -> !peer.isActive())
        .collect(Collectors.toList());

    // if len(peers) >= 0, disconnect randomly
    if (!peers.isEmpty()) {
      List<Channel> list = new ArrayList<>(peers);
      Channel peer = list.get(new Random().nextInt(peers.size()));
      log.info("Disconnect with peer randomly: {}", peer);
      peer.close();
    }
  }

  private synchronized void logActivePeers() {
    log.info("Peer stats: channels {}, activePeers {}, active {}, passive {}",
        ChannelManager.getChannels().size(), activePeers.size(), activePeersCount.get(),
        passivePeersCount.get());
  }

  @Override
  public synchronized void onConnect(Channel peer) {
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
