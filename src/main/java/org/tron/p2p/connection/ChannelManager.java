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
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.connection.socket.PeerServer;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.connection.business.KeepAliveTask;
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
  private static final Map<String, Channel> channels = new ConcurrentHashMap<>();

  @Getter
  private static final Cache<InetAddress, Node> bannedNodes = CacheBuilder
          .newBuilder().maximumSize(2000).build();

  @Getter
  private Map<InetAddress, Node> activeNodes = new ConcurrentHashMap();

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
    }


    syncPool.init(this, peerClient, nodeManager);

    keepAliveTask.init(this);
  }

  public void connect(InetSocketAddress address) {

  }

  public Collection<Channel> getActiveChannels() {
    return channels.values();
  }

  public void notifyDisconnect(Channel channel) {
    syncPool.onDisconnect(channel);
    channels.values().remove(channel);
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

  public static int getConnectionNum(InetAddress inetAddress) {
    int cnt = 0;
    for (Channel channel : channels.values()) {
      if (channel.getInetAddress().equals(inetAddress)) {
        cnt++;
      }
    }
    return cnt;
  }

  public static synchronized DisconnectCode processPeer(Channel channel) {

    if (!Parameter.p2pConfig.getTrustNodes().contains(channel.getInetAddress())) {

      if (bannedNodes.getIfPresent(channel.getInetAddress()) != null) {
        log.info("Peer {} recently disconnected", channel.getInetAddress());
        return DisconnectCode.TIME_BANNED;
      }

      if (!channel.isActive() && channels.size() >= Parameter.p2pConfig.getMaxConnections()) {
        return DisconnectCode.TOO_MANY_PEERS;
      }

      int num = getConnectionNum(channel.getInetAddress());
      if (num >= Parameter.p2pConfig.getMaxConnectionsWithSameIp()) {
        return DisconnectCode.MAX_CONNECTION_WITH_SAME_IP;
      }
    }

    String nodeId = channel.getNode().getHexId();
    Channel c2 = channels.get(nodeId);
    if (c2 != null) {
      if (c2.getStartTime() > channel.getStartTime()) {
        c2.close();
      } else {
        return DisconnectCode.DUPLICATE_PEER;
      }
    }
    channels.put(nodeId, channel);
    log.info("Add peer {}, total peers: {}", channel.getInetAddress(), channels.size());
    return DisconnectCode.NORMAL;
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
