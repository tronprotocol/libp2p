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
import org.tron.p2p.dns.DnsManager;
import org.tron.p2p.dns.DnsNode;
import org.tron.p2p.exception.P2pException;
import org.tron.p2p.utils.CollectionUtils;
import org.tron.p2p.utils.NetUtil;

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

  private void addNode(Set<InetSocketAddress> inetSet, Node node) {
    if (node != null) {
      if (node.getInetSocketAddressV4() != null) {
        inetSet.add(node.getInetSocketAddressV4());
      }
      if (node.getInetSocketAddressV6() != null) {
        inetSet.add(node.getInetSocketAddressV6());
      }
    }
  }

  private void connect() {
    List<Node> connectNodes = new ArrayList<>();

    //collect already used nodes in channelManager
    Set<InetSocketAddress> inetSocketAddresses = new HashSet<>();
    Set<InetAddress> addressInUse = new HashSet<>();
    Set<String> nodesInUse = new HashSet<>();
    nodesInUse.add(Hex.toHexString(p2pConfig.getNodeID()));
    ChannelManager.getChannels().values().forEach(channel -> {
      if (StringUtils.isNotEmpty(channel.getNodeId())) {
        nodesInUse.add(channel.getNodeId());
      }
      addressInUse.add(channel.getInetAddress());
      addNode(inetSocketAddresses, channel.getNode());
    });

    p2pConfig.getActiveNodes().forEach(address -> {
      if (!addressInUse.contains(address.getAddress())) {
        addressInUse.add(address.getAddress());
        inetSocketAddresses.add(address);
        Node node = new Node(address); //use a random NodeId for config activeNodes
        connectNodes.add(node);
        if (node.getPreferInetSocketAddress() != null) {
          addressInUse.add(address.getAddress());
          connectNodes.add(node);
        }
      }
    });
    addNode(inetSocketAddresses, new Node(Parameter.p2pConfig.getNodeID(), Parameter.p2pConfig.getIp(),
        Parameter.p2pConfig.getIpv6(), Parameter.p2pConfig.getPort()));

    //calculate lackSize exclude config activeNodes
    int size = Math.max(p2pConfig.getMinConnections() - activePeers.size(),
        p2pConfig.getMinActiveConnections() - activePeersCount.get());
    int lackSize = size - connectNodes.size();
    if (lackSize > 0) {
      List<Node> connectableNodes = ChannelManager.getNodeDetectService().getConnectableNodes();
      for (Node node : connectableNodes) {
        if (validNode(node, nodesInUse, inetSocketAddresses)) {
          connectNodes.add(node);
          nodesInUse.add(node.getHexId());
          inetSocketAddresses.add(node.getPreferInetSocketAddress());
          lackSize -= 1;
          if (lackSize <= 0) {
            break;
          }
        }
      }
    }

    if (lackSize > 0) {
      List<Node> connectableNodes = NodeManager.getConnectableNodes();
      List<Node> newNodes = getNodes(inetSocketAddresses, nodesInUse, connectableNodes, lackSize);
      connectNodes.addAll(newNodes);
      for (Node node : newNodes) {
        nodesInUse.add(node.getHexId());
        inetSocketAddresses.add(node.getPreferInetSocketAddress());
      }
      lackSize -= newNodes.size();
    }

    if (lackSize > 0) {
      List<DnsNode> dnsNodes = DnsManager.getDnsNodes();
      log.debug("Compatible dns nodes size:{}", dnsNodes.size());
      List<DnsNode> filtered = new ArrayList<>();
      for (DnsNode node : dnsNodes) {
        if (validNode(node, nodesInUse, inetSocketAddresses)) {
          DnsNode copyNode = (DnsNode) node.clone();
          copyNode.setId(NetUtil.getNodeId());
          inetSocketAddresses.add(copyNode.getPreferInetSocketAddress());
          filtered.add(copyNode);
        }
      }
      Collections.shuffle(filtered);
      List<DnsNode> newNodes = CollectionUtils.truncate(filtered, lackSize);
      connectNodes.addAll(newNodes);
    }

    //log.info("Lack size:{}, connectNodes size:{}", size, connectNodes.size());
    //establish tcp connection with chose nodes by peerClient
    connectNodes.forEach(n -> {
      log.info("Connect to peer {}", n.getPreferInetSocketAddress());
      peerClient.connectAsync(n, false);
      peerClientCache.put(n.getPreferInetSocketAddress().getAddress(),
              System.currentTimeMillis());
    });
  }

  public List<Node> getNodes(Set<InetSocketAddress> inetSocketAddresses, Set<String> nodesInUse,
                             List<Node> connectableNodes, int limit) {
    List<Node> filtered = new ArrayList<>();

    for (Node node : connectableNodes) {
      if (validNode(node, nodesInUse, inetSocketAddresses)) {
        filtered.add((Node) node.clone());
      }
    }

    filtered.sort(Comparator.comparingLong(node -> -node.getUpdateTime()));
    return CollectionUtils.truncate(filtered, limit);
  }

  private boolean validNode(Node node, Set<String> nodesInUse, Set<InetSocketAddress> inetInUse) {
    long now = System.currentTimeMillis();
    InetSocketAddress inetSocketAddress = node.getPreferInetSocketAddress();
    InetAddress inetAddress = inetSocketAddress.getAddress();
    Long forbiddenTime = ChannelManager.getBannedNodes().getIfPresent(inetAddress);
    if ((forbiddenTime != null && now <= forbiddenTime)
        || (ChannelManager.getConnectionNum(inetAddress)
        >= p2pConfig.getMaxConnectionsWithSameIp())
        || (node.getId() != null && nodesInUse.contains(node.getHexId()))
        || (peerClientCache.getIfPresent(inetAddress) != null)
        || inetInUse.contains(inetSocketAddress)) {
      return false;
    }
    return true;
  }

  private void check() {
    if (ChannelManager.getChannels().size() < p2pConfig.getMaxConnections()) {
      return;
    }

    List<Channel> channels = new ArrayList<>(activePeers);
    Collection<Channel> peers = channels.stream()
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
    List<Channel> channels = new ArrayList<>(activePeers);
    try {
      channels.forEach(p -> {
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
