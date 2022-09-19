package org.tron.p2p.connection;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import lombok.Getter;
import lombok.Setter;

public class Channel {

  @Getter
  @Setter
  long lastSendTime = 0;

  @Getter
  private volatile long disconnectTime;

  @Getter
  private boolean isActive;
  private ChannelHandlerContext ctx;
  private InetSocketAddress inetSocketAddress;

  @Getter
  private volatile boolean isDisconnect;

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

  public void close() {
    //todo
  }

  public void setChannelHandlerContext(ChannelHandlerContext ctx) {
    this.ctx = ctx;
    this.inetSocketAddress = ctx == null ? null : (InetSocketAddress) ctx.channel().remoteAddress();
  }

  public InetAddress getInetAddress() {
    return ctx == null ? null : ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress();
  }
}
