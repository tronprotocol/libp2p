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
import org.bouncycastle.util.encoders.Hex;
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.connection.socket.PeerServer;
import org.tron.p2p.discover.Node;

@Slf4j(topic = "net")
public class ChannelManager {

  @Getter
  private static final Map<String, Channel> channels = new ConcurrentHashMap<>();

  @Getter
  private static final Cache<InetAddress, Node> bannedNodes = CacheBuilder
          .newBuilder().maximumSize(2000).build();

  @Getter
  private Map<InetAddress, Node> activeNodes = new ConcurrentHashMap();

  public void init() {

  }

  public void connect(InetSocketAddress address) {

  }

  public Collection<Channel> getActiveChannels() {
    return channels.values();
  }

  public void notifyDisconnect(Channel channel) {

  }

  public void close() {

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
}
