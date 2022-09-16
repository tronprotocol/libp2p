package org.tron.p2p.connection;

import io.netty.channel.ChannelPipeline;

public class Channel {

  public void init(ChannelPipeline pipeline, String remoteId, boolean discoveryMode,
      ChannelManager channelManager) {
  }

  void disconnect(Channel c, int code) {
  }

  void send(byte[] b) {
  }

  public boolean isDisconnect() {
    return true;
  }
}
