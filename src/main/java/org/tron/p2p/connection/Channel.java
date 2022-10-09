package org.tron.p2p.connection;

import com.google.common.base.Throwables;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.tron.p2p.base.Parameter;
import org.tron.p2p.connection.message.Message;
import org.tron.p2p.connection.socket.MessageHandler;
import org.tron.p2p.connection.socket.P2pProtobufVarint32FrameDecoder;
import org.tron.p2p.exception.P2pException;
import org.tron.p2p.stats.TrafficStats;
import org.tron.p2p.utils.ByteArray;

@Slf4j(topic = "net")
public class Channel {

  public volatile boolean waitForPong = false;
  public volatile long pingSent;

  private ChannelHandlerContext ctx;
  @Getter
  @Setter
  private InetSocketAddress inetSocketAddress;
  @Getter
  @Setter
  private InetAddress inetAddress;
  private MessageHandler messageHandler;
  @Getter
  private volatile long disconnectTime;
  @Getter
  private volatile boolean isDisconnect = false;
  @Getter
  @Setter
  private long lastSendTime = System.currentTimeMillis();
  @Getter
  private final long startTime = System.currentTimeMillis();
  @Getter
  private boolean isActive;
  @Getter
  private boolean isTrustPeer;
  @Getter
  @Setter
  private volatile boolean finishHandshake;
  @Getter
  @Setter
  private String nodeId;
  @Getter
  private boolean discoveryMode;

  public void init(ChannelPipeline pipeline, String nodeId, boolean discoveryMode) {
    this.discoveryMode = discoveryMode;
    this.nodeId = nodeId;
    this.isActive = nodeId != null && !nodeId.isEmpty();
    this.messageHandler = new MessageHandler(this);
    pipeline.addLast("readTimeoutHandler", new ReadTimeoutHandler(60, TimeUnit.SECONDS));
    pipeline.addLast(TrafficStats.tcp);
    pipeline.addLast("protoPrepend", new ProtobufVarint32LengthFieldPrepender());
    pipeline.addLast("protoDecode", new P2pProtobufVarint32FrameDecoder(this));
    pipeline.addLast("messageHandler", messageHandler);
  }

  public void send(Message message) {
    if (isDisconnect) {
      log.warn("Send to {} failed as channel has closed, message-type:{} ",
          ctx.channel().remoteAddress(), ByteArray.byte2int(message.getType().getType()));
      return;
    }
    ctx.writeAndFlush(message.getSendData()).addListener((ChannelFutureListener) future -> {
      if (!future.isSuccess() && !isDisconnect) {
        log.warn("Send to {} failed, message-type:{}, cause:{}",
            ctx.channel().remoteAddress(), ByteArray.byte2int(message.getType().getType()),
            future.cause().getMessage());
      }
    });
    setLastSendTime(System.currentTimeMillis());
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

  public void setChannelHandlerContext(ChannelHandlerContext ctx) {
    this.ctx = ctx;
    this.inetSocketAddress = (InetSocketAddress) ctx.channel().remoteAddress();
    this.inetAddress = inetSocketAddress.getAddress();
    this.isTrustPeer = Parameter.p2pConfig.getTrustNodes().contains(inetSocketAddress);
  }

  public void close() {
    close(System.currentTimeMillis() + Parameter.DEFAULT_BAN_TIME);
  }

  private void close(long banTime) {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();
    ChannelManager.banNode(this.inetAddress, banTime);
    ctx.close();
  }
}
