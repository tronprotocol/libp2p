package org.tron.p2p.connection;

import com.google.common.base.Throwables;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.exception.P2pException;

import java.io.IOException;
import java.net.SocketAddress;

@Slf4j(topic = "net")
public class Channel {
  @Setter
  private ChannelHandlerContext ctx;
  @Getter
  private volatile long disconnectTime;
  private volatile boolean isDisconnect;

  public void init(ChannelPipeline pipeline, String remoteId, boolean discoveryMode,
      ChannelManager channelManager) {
  }

  public void disconnect(Channel channel, int code) {
  }

  public void send(byte[] data) {
    if (isDisconnect) {
      log.warn("Send to {} failed as channel has closed, message-type:{} ",
              ctx.channel().remoteAddress(), data[0]);
      return;
    }
    ctx.writeAndFlush(data).addListener((ChannelFutureListener) future -> {
      if (!future.isSuccess() && !isDisconnect) {
        log.warn("Send to {} failed, message-type:{}",
                ctx.channel().remoteAddress(), data[0]);
      }
    });
  }

  public void processException(Throwable throwable) {
    Throwable baseThrowable = throwable;
    try {
      baseThrowable = Throwables.getRootCause(baseThrowable);
    } catch (IllegalArgumentException e) {
      baseThrowable = e.getCause();
      log.warn("Loop in causal chain detected");
    }
    SocketAddress address = ctx.channel().remoteAddress();
    if (throwable instanceof ReadTimeoutException
            || throwable instanceof IOException) {
      log.warn("Close peer {}, reason: {}", address, throwable.getMessage());
    } else if (baseThrowable instanceof P2pException) {
      log.warn("Close peer {}, type: {}, info: {}",
              address, ((P2pException) baseThrowable).getType(), baseThrowable.getMessage());
    } else {
      log.error("Close peer {}, exception caught", address, throwable);
    }
    close();
  }

  public void close() {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();
    ctx.close();
  }

  public boolean isDisconnect() {
    return isDisconnect;
  }
}
