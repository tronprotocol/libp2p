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
import org.tron.p2p.discover.Node;
import org.tron.p2p.protos.Discover.ReasonCode;
import org.tron.p2p.utils.ByteArrayWrapper;

public class ChannelManager {

  @Getter
  private final Map<ByteArrayWrapper, Channel> nodeId2Channels = new ConcurrentHashMap<>();

  @Getter
  private Cache<InetAddress, Node> trustNodes = CacheBuilder.newBuilder().maximumSize(100).build();

  @Getter
  private Map<InetAddress, Node> activeNodes = new ConcurrentHashMap();

  @Getter
  private Cache<InetAddress, ReasonCode> badPeers = CacheBuilder.newBuilder().maximumSize(10000)
      .expireAfterWrite(1, TimeUnit.HOURS).recordStats().build();

  @Getter
  private Cache<InetAddress, ReasonCode> recentlyDisconnected = CacheBuilder.newBuilder()
      .maximumSize(1000).expireAfterWrite(30, TimeUnit.SECONDS).recordStats().build();

  public void init() {

  }

  public void connect(InetSocketAddress address) {

  }

  public Collection<Channel> getActiveChannels() {
    return nodeId2Channels.values();
  }

  public void notifyDisconnect(Channel channel) {

  }

  public void close() {

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
}
