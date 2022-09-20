package org.tron.p2p.connection.business;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.connection.Channel;
import org.tron.p2p.connection.message.Message;

@Slf4j(topic = "net")
public class MessageSend {

  private BlockingQueue<Message> msgQueue = new LinkedBlockingQueue<Message>();

  public void init() {
  }

  public void close() {
  }

  public void send(Channel channel, byte[] b) {
  }

  public void fastSend(Channel channel, ChannelHandlerContext ctx, Message msg) {
    if (channel.isDisconnect()) {
      log.warn("Fast send to {} failed as channel has closed, {} ",
          ctx.channel().remoteAddress(), msg);
      return;
    }
    log.info("Fast send to {}, {} ", ctx.channel().remoteAddress(), msg);
    ctx.writeAndFlush(msg.getData()).addListener((ChannelFutureListener) future -> {
      if (!future.isSuccess() && !channel.isDisconnect()) {
        log.warn("Fast send to {} failed, {}", ctx.channel().remoteAddress(), msg);
      }
    });
  }

}
