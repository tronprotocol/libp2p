package org.tron.p2p.connection;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.connection.business.KeepAliveTask;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.connection.socket.PeerServer;
import org.tron.p2p.connection.socket.SyncPool;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;

@Slf4j(topic = "net")
public class ChannelManager {

  private PeerServer peerServer;

  private PeerClient peerClient;

  private SyncPool syncPool;

  private KeepAliveTask keepAliveTask;

  @Getter
  private final Map<String, Channel> nodeId2Channels = new ConcurrentHashMap<>();

  @Getter
  private Cache<InetAddress, Node> trustNodes = CacheBuilder.newBuilder().maximumSize(100).build();

  @Getter
  private Map<InetAddress, Node> activeNodes = new ConcurrentHashMap();

//  @Getter
//  private Cache<InetAddress, ReasonCode> badPeers = CacheBuilder.newBuilder().maximumSize(10000)
//      .expireAfterWrite(1, TimeUnit.HOURS).recordStats().build();
//
//  @Getter
//  private Cache<InetAddress, ReasonCode> recentlyDisconnected = CacheBuilder.newBuilder()
//      .maximumSize(1000).expireAfterWrite(30, TimeUnit.SECONDS).recordStats().build();

  public void init(P2pConfig p2pConfig, NodeManager nodeManager) {
    if (p2pConfig.getPort() > 0) {
      new Thread(() -> peerServer.start(p2pConfig.getPort()), "PeerServerThread").start();
    }

    peerClient = new PeerClient();

    for (InetSocketAddress inetSocketAddress : p2pConfig.getActiveNodes()) {
      InetAddress inetAddress = inetSocketAddress.getAddress();
      Node node = Node.instanceOf(inetAddress.getHostAddress(), inetSocketAddress.getPort());
      activeNodes.put(inetAddress, node);
    }

    for (InetSocketAddress inetSocketAddress : p2pConfig.getTrustNodes()) {
      InetAddress inetAddress = inetSocketAddress.getAddress();
      Node node = Node.instanceOf(inetAddress.getHostAddress(), inetSocketAddress.getPort());
      trustNodes.put(inetAddress, node);
    }

    log.info("Node config, trust {}, active {}", trustNodes.size(), activeNodes.size());

    syncPool.init(this, peerClient, nodeManager);

    keepAliveTask.init(this);
  }

  public void connect(InetSocketAddress address) {

  }

  public Collection<Channel> getActiveChannels() {
    return nodeId2Channels.values();
  }

  public void notifyDisconnect(Channel channel) {
    syncPool.onDisconnect(channel);
    nodeId2Channels.values().remove(channel);
//    if (channel != null) {
//      if (channel.getNodeStatistics() != null) {
//        channel.getNodeStatistics().notifyDisconnect();
//      }
//      InetAddress inetAddress = channel.getInetAddress();
//      if (inetAddress != null && recentlyDisconnected.getIfPresent(inetAddress) == null) {
//        recentlyDisconnected.put(channel.getInetAddress(), UNKNOWN);
//      }
//    }
  }

  public int getConnectionNum(InetAddress inetAddress) {
    int cnt = 0;
    for (Channel channel : nodeId2Channels.values()) {
      if (channel.getInetAddress().equals(inetAddress)) {
        cnt++;
      }
    }
    return cnt;
  }

  public void processDisconnect(Channel channel, int code) {
    InetAddress inetAddress = channel.getInetAddress();
    if (inetAddress == null) {
      return;
    }
//    switch (reason) {
//      case BAD_PROTOCOL:
//      case BAD_BLOCK:
//      case BAD_TX:
//        badPeers.put(channel.getInetAddress(), reason);
//        break;
//      default:
//        recentlyDisconnected.put(channel.getInetAddress(), reason);
//        break;
//    }
//    MetricsUtil.counterInc(MetricsKey.NET_DISCONNECTION_COUNT);
//    MetricsUtil.counterInc(MetricsKey.NET_DISCONNECTION_DETAIL + reason);
//    Metrics.counterInc(MetricKeys.Counter.P2P_DISCONNECT, 1,
//        reason.name().toLowerCase(Locale.ROOT));
  }

  public void close() {
    syncPool.close();
    peerServer.close();
    peerClient.close();
  }

}
