package org.tron.p2p.connection;

import io.netty.channel.ChannelPipeline;

public class Channel {

  public void init(ChannelPipeline pipeline, String remoteId, boolean discoveryMode,
      ChannelManager channelManager) {
  }

  void disconnect(Channel channel, int code) {
  }

  void send(byte[] bytes) {
  }

  public boolean isDisconnect() {
    return true;
  }
}
