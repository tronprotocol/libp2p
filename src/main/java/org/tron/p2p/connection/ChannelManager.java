package org.tron.p2p.connection;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import org.tron.p2p.utils.ByteArrayWrapper;

public class ChannelManager {

  @Getter
  private final Map<ByteArrayWrapper, Channel> activeChannels = new ConcurrentHashMap<>();

  public void init() {

  }

  public void connect(InetSocketAddress address) {

  }

  public void notifyDisconnect(Channel channel) {

  }

  public void close() {

  }
}
