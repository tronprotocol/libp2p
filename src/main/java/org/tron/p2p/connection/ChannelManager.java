package org.tron.p2p.connection;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.P2pConfig;
import org.tron.p2p.config.Parameter;
import org.tron.p2p.connection.business.KeepAliveTask;
import org.tron.p2p.connection.business.handshake.DisconnectCode;
import org.tron.p2p.connection.business.handshake.HandshakeService;
import org.tron.p2p.connection.socket.PeerClient;
import org.tron.p2p.connection.socket.PeerServer;
import org.tron.p2p.connection.socket.SyncPool;
import org.tron.p2p.discover.Node;
import org.tron.p2p.discover.NodeManager;
import org.tron.p2p.utils.ByteArray;

@Slf4j(topic = "net")
public class ChannelManager {

  private PeerServer peerServer;

  private PeerClient peerClient;

  @Getter
  private SyncPool syncPool;

  private KeepAliveTask keepAliveTask;

  private HandshakeService handshakeService;

  @Getter
  private static final Map<String, Channel> channels = new ConcurrentHashMap<>();

  @Getter
  private static final Cache<InetAddress, Node> bannedNodes = CacheBuilder
      .newBuilder().maximumSize(2000).build();

  @Getter
  private Cache<InetAddress, DisconnectCode> recentlyDisconnected = CacheBuilder.newBuilder()
      .maximumSize(1000).expireAfterWrite(30, TimeUnit.SECONDS).recordStats().build();

  @Getter
  private Map<InetAddress, Node> activeNodes = new ConcurrentHashMap();

  private P2pConfig p2pConfig;

  public ChannelManager() {
    peerServer = new PeerServer(this);
    peerClient = new PeerClient(this);
    keepAliveTask = new KeepAliveTask(this);
    syncPool = new SyncPool(this);
    handshakeService = new HandshakeService();
  }

  public void init(NodeManager nodeManager) {
    this.p2pConfig = Parameter.p2pConfig;
    if (this.p2pConfig.getPort() > 0) {
      peerServer.setNodeManager(nodeManager);
      new Thread(() -> peerServer.start(p2pConfig.getPort()), "PeerServerThread").start();
    }

    peerClient.setNodeManager(nodeManager);

    for (InetSocketAddress inetSocketAddress : p2pConfig.getActiveNodes()) {
      InetAddress inetAddress = inetSocketAddress.getAddress();
      Node node = Node.instanceOf(inetAddress.getHostAddress(), inetSocketAddress.getPort());
      activeNodes.put(inetAddress, node);
    }

    syncPool.init(peerClient, nodeManager);

    keepAliveTask.init();
  }

  //used by fast forward node
  public void connect(InetSocketAddress address) {
    peerClient.connect(address.getAddress().getHostAddress(), address.getPort(),
        ByteArray.toHexString(Node.getNodeId()));
  }

  public Collection<Channel> getActiveChannels() {
    return channels.values();
  }

  public void notifyDisconnect(Channel channel) {
    syncPool.onDisconnect(channel);
    //channels.remove(channel.getNode().getHexId());
    channels.values().remove(channel); //todo why remove from values, not remove key?

    if (channel != null) {
      InetAddress inetAddress = channel.getInetAddress();
      if (inetAddress != null && recentlyDisconnected.getIfPresent(inetAddress) == null) {
        recentlyDisconnected.put(channel.getInetAddress(), DisconnectCode.UNKNOWN);
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

  //invoke by handshake service
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

  public void processDisconnect(Channel channel, DisconnectCode code) {
    InetAddress inetAddress = channel.getInetAddress();
    if (inetAddress == null) {
      return;
    }
    switch (code) {
      case DIFFERENT_VERSION:
        bannedNodes.put(channel.getInetAddress(), channel.getNode());
        break;
      default:
        recentlyDisconnected.put(channel.getInetAddress(), code);
        break;
    }
  }

  public void close() {
    syncPool.close();
    peerServer.close();
    peerClient.close();
  }

}
