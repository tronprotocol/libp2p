package org.tron.p2p.connection;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.P2pEventHandler;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.business.handshake.HandshakeService;
import org.tron.p2p.connection.business.keepalive.KeepAliveService;
import org.tron.p2p.connection.business.pool.ConnPoolService;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.connection.socket.PeerServer;
import org.tron.p2p.exception.P2pException;
import org.tron.p2p.utils.ByteArray;
import org.tron.p2p.utils.NetUtil;

@Slf4j(topic = "net")
public class ChannelManager {

  private static PeerServer peerServer;

  @Getter
  private static PeerClient peerClient;

  @Getter
  private static ConnPoolService connPoolService;

  private static KeepAliveService keepAliveService;

  @Getter
  private static HandshakeService handshakeService;

  @Getter
  private static final Map<InetSocketAddress, Channel> channels = new ConcurrentHashMap<>();

  @Getter
  private static final Cache<InetAddress, Long> bannedNodes = CacheBuilder
      .newBuilder().maximumSize(2000).build();

  public static void init() {
    peerServer = new PeerServer();
    peerClient = new PeerClient();
    keepAliveService = new KeepAliveService();
    connPoolService = new ConnPoolService();
    handshakeService = new HandshakeService();
    peerServer.init();
    peerClient.init();
    keepAliveService.init();
    connPoolService.init(peerClient);
  }

  public static void connect(InetSocketAddress address) {
    peerClient.connect(address.getAddress().getHostAddress(), address.getPort(),
        ByteArray.toHexString(NetUtil.getNodeId()));
  }

  public static void notifyDisconnect(Channel channel) {
    if (channel != null) {
      channels.remove(channel.getInetSocketAddress());
      Parameter.handlerList.forEach(h -> h.onDisconnect(channel));
      InetAddress inetAddress = channel.getInetAddress();
      if (bannedNodes.getIfPresent(inetAddress) == null) {
        banNode(channel.getInetAddress(), Parameter.DEFAULT_BAN_TIME);
      }
    }
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

    if (!Parameter.p2pConfig.getTrustNodes().contains(channel.getInetSocketAddress())) {

      InetAddress inetAddress = channel.getInetAddress();
      if (bannedNodes.getIfPresent(inetAddress) != null
          && bannedNodes.getIfPresent(inetAddress) > System.currentTimeMillis()) {
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

    if (channel.getNodeId() != null) {
      for (Channel c : channels.values()) {
        if (channel.getNodeId().equals(c.getNodeId())) {
          if (c.getStartTime() > channel.getStartTime()) {
            c.close();
          } else {
            return DisconnectCode.DUPLICATE_PEER;
          }
        }
      }
    }

    channels.put(channel.getInetSocketAddress(), channel);
    log.info("Add peer {}, total peers: {}", channel.getInetAddress(), channels.size());
    return DisconnectCode.NORMAL;
  }

  public static void banNode(InetAddress inetAddress, Long banTime) {
    bannedNodes.put(inetAddress, System.currentTimeMillis() + banTime);
  }

  public static void close() {
    connPoolService.close();
    keepAliveService.close();
    peerClient.close();
    peerServer.close();
  }

  public static void processMessage(Channel channel, byte[] data) throws P2pException {
    channel.setLastSendTime(System.currentTimeMillis());
    Message message = Message.parse(data);
    log.info("MessageType:{}", ByteArray.byte2int(message.getType().getType()));
    switch (message.getType()) {
      case KEEP_ALIVE_PING:
      case KEEP_ALIVE_PONG:
        keepAliveService.processMessage(channel, message);
        break;
      case HANDSHAKE_HELLO:
        handshakeService.processMessage(channel, message);
        break;
      default:
        handMessage(channel, data);
        break;
    }
  }

  private static void handMessage(Channel channel, byte[] data) throws P2pException {
    P2pEventHandler handler = Parameter.handlerMap.get(data[0]);
    if (handler == null) {
      throw new P2pException(P2pException.TypeEnum.NO_SUCH_MESSAGE, "type:" + data[0]);
    }

    if (!channel.isFinishHandshake()) {
      channel.setFinishHandshake(true);
      Parameter.handlerList.forEach(h -> h.onConnect(channel)); //why all handler onConnect
    }
    handler.onMessage(channel, data);
  }

  public synchronized static void updateNodeId(Channel channel, String nodeId) {
    channel.setNodeId(nodeId);
    List<Channel> list = new ArrayList<>();
    channels.values().forEach(c -> {
      if (nodeId.equals(channel.getNodeId())) {
        list.add(c);
      }
    });
    if (list.size() <= 1) {
      return;
    }
    Channel c1 = list.get(0);
    Channel c2 = list.get(1);
    if (c1.getStartTime() > c2.getStartTime()) {
      c1.close();
    } else {
      c2.close();
    }
  }

}
