package org.tron.p2p.connection.socket;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.ChannelManager;

@Slf4j(topic = "net")
public class P2pChannelInitializer extends ChannelInitializer<NioSocketChannel> {

  private final String remoteId;

  private boolean peerDiscoveryMode = false; //only be true when channel is activated by detect service

  private boolean trigger = true;
  public P2pChannelInitializer(String remoteId, boolean peerDiscoveryMode, boolean trigger) {
    this.remoteId = remoteId;
    this.peerDiscoveryMode = peerDiscoveryMode;
    this.trigger = trigger;
  }

  @Override
  public void initChannel(NioSocketChannel ch) {
    try {
      if (StringUtils.isEmpty(remoteId) && !PendingInboundConnectionHandler.tryAdd(ch)) {
        ch.close();
        return;
      }
      final Channel channel = new Channel();
      channel.init(ch.pipeline(), remoteId, peerDiscoveryMode);

      // limit the size of receiving buffer to 1024
      ch.config().setRecvByteBufAllocator(new FixedRecvByteBufAllocator(256 * 1024));
      ch.config().setOption(ChannelOption.SO_RCVBUF, 256 * 1024);
      ch.config().setOption(ChannelOption.SO_BACKLOG, 1024);

      // Bound the per-channel outbound buffer: once unsent bytes exceed the high water mark the
      // channel reports not-writable, so callers serving bulk replies can apply backpressure
      // instead of letting a slow or non-reading peer accumulate unsent data without limit.
      int highWaterMark = Parameter.WRITE_BUFFER_HIGH_WATER_MARK;
      ch.config().setWriteBufferWaterMark(
          new WriteBufferWaterMark(highWaterMark / 2, highWaterMark));

      // be aware of channel closing
      ch.closeFuture().addListener((ChannelFutureListener) future -> {
        channel.markDisconnected();
        if (channel.isDiscoveryMode() && !channel.isRegisteredPeer()) {
          ChannelManager.getNodeDetectService().notifyDisconnect(channel);
        } else {
          try {
            log.info("Close channel:{}", channel.getInetSocketAddress());
            ChannelManager.notifyDisconnect(channel);
          } finally {
            if (channel.getInetSocketAddress() != null && channel.isActive() && trigger) {
              ChannelManager.triggerConnect(channel.getInetSocketAddress());
            }
          }
        }
      });

    } catch (Exception e) {
      log.error("Unexpected initChannel error", e);
      ch.close();
    }
  }

}
