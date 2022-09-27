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
import org.tron.p2p.connection.socket.MessageHandler;
import org.tron.p2p.connection.socket.P2pProtobufVarint32FrameDecoder;
import org.tron.p2p.discover.Node;
import org.tron.p2p.exception.P2pException;
import org.tron.p2p.stats.P2pStats;
import org.tron.p2p.stats.TrafficStats;

@Slf4j(topic = "net")
public class Channel {

  private ChannelHandlerContext ctx;
  private P2pStats p2pStats;
  private MessageHandler messageHandler;

  @Getter
  private volatile long disconnectTime;
  @Getter
  private volatile boolean isDisconnect;
  @Getter
  @Setter
  private long lastSendTime = 0;
  @Getter
  private final long startTime = System.currentTimeMillis();
  @Getter
  private boolean isActive;
  @Getter
  private boolean isTrustPeer;
  private InetSocketAddress inetSocketAddress;
  @Getter
  private Node node;
  @Getter
  @Setter
  private volatile boolean finishHandshake;

  public volatile boolean waitForPong = false;
  private boolean discoveryMode;


  public void init(ChannelPipeline pipeline, String remoteId, boolean discoveryMode) {
    this.discoveryMode = discoveryMode;
    this.isActive = remoteId != null && !remoteId.isEmpty();
    this.messageHandler = new MessageHandler(this);

    pipeline.addLast("readTimeoutHandler", new ReadTimeoutHandler(60, TimeUnit.SECONDS));
    pipeline.addLast(TrafficStats.tcp);
    pipeline.addLast("protoPrepend", new ProtobufVarint32LengthFieldPrepender());
    pipeline.addLast("protoDecode", new P2pProtobufVarint32FrameDecoder(this));
    pipeline.addLast("messageHandler", messageHandler);
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
    isTrustPeer = Parameter.p2pConfig.getTrustNodes().contains(this.inetSocketAddress);
  }

  public InetAddress getInetAddress() {
    return inetSocketAddress.getAddress();
  }

  public String getPeerId() {
    return node == null ? "null" : node.getHexId();
  }

  public void close() {
    close(ChannelManager.DEFAULT_BAN_TIME);
  }

  public void close(Long banTime) {
    this.isDisconnect = true;
    this.disconnectTime = System.currentTimeMillis();
    ChannelManager.banNode(getInetAddress(), banTime);
    ctx.close();
  }
}
